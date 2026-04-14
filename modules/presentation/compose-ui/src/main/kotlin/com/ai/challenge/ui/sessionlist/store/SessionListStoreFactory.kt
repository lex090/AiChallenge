package com.ai.challenge.ui.sessionlist.store

import arrow.core.getOrElse
import com.ai.challenge.conversation.service.SessionService
import com.ai.challenge.conversation.model.SessionTitle
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class SessionListStoreFactory(
    private val storeFactory: StoreFactory,
    private val sessionService: SessionService,
) {
    fun create(): SessionListStore =
        object : SessionListStore,
            Store<SessionListStore.Intent, SessionListStore.State, SessionListStore.Label> by storeFactory.create(
                name = "SessionListStore",
                initialState = SessionListStore.State(
                    sessions = emptyList(),
                    activeSessionId = null,
                    filterProjectId = null,
                    showFreeSessions = false,
                    errorText = null,
                ),
                executorFactory = { ExecutorImpl(sessionService = sessionService) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class SessionsLoaded(val sessions: List<SessionListStore.SessionItem>, val activeSessionId: AgentSessionId?) : Msg
        data class SessionCreated(val item: SessionListStore.SessionItem) : Msg
        data class SessionDeleted(val id: AgentSessionId, val newActiveId: AgentSessionId?) : Msg
        data class SessionSelected(val id: AgentSessionId) : Msg
        data class ProjectFilterChanged(val projectId: ProjectId?, val showFreeSessions: Boolean) : Msg
        data class Error(val text: String) : Msg
    }

    private class ExecutorImpl(
        private val sessionService: SessionService,
    ) : CoroutineExecutor<SessionListStore.Intent, Nothing, SessionListStore.State, Msg, SessionListStore.Label>() {

        override fun executeIntent(intent: SessionListStore.Intent) {
            when (intent) {
                is SessionListStore.Intent.LoadSessions -> handleLoadSessions()
                is SessionListStore.Intent.CreateSession -> handleCreateSession()
                is SessionListStore.Intent.DeleteSession -> handleDeleteSession(id = intent.id)
                is SessionListStore.Intent.SelectSession -> dispatch(Msg.SessionSelected(id = intent.id))
                is SessionListStore.Intent.FilterByProject -> handleFilterByProject(projectId = intent.projectId)
            }
        }

        private fun handleFilterByProject(projectId: ProjectId?) {
            val showFree = projectId == null
            dispatch(Msg.ProjectFilterChanged(projectId = projectId, showFreeSessions = showFree))
            handleLoadSessions()
        }

        private fun handleLoadSessions() {
            scope.launch {
                sessionService.list()
                    .fold(
                        ifLeft = { error -> dispatch(Msg.Error(text = error.message)) },
                        ifRight = { sessionList ->
                            val currentState = state()
                            val filtered = sessionList.filter { session ->
                                when {
                                    currentState.showFreeSessions -> session.projectId == null
                                    currentState.filterProjectId != null -> session.projectId == currentState.filterProjectId
                                    else -> false
                                }
                            }
                            val sessions = filtered.map { session ->
                                SessionListStore.SessionItem(
                                    id = session.id,
                                    title = session.title.value,
                                    updatedAt = session.updatedAt.value,
                                    projectId = session.projectId,
                                )
                            }
                            val currentActiveInList = sessions.any { it.id == currentState.activeSessionId }
                            val newActiveId = if (currentActiveInList) {
                                currentState.activeSessionId
                            } else {
                                sessions.firstOrNull()?.id
                            }
                            dispatch(Msg.SessionsLoaded(sessions = sessions, activeSessionId = newActiveId))
                            if (newActiveId != currentState.activeSessionId) {
                                publish(SessionListStore.Label.ActiveSessionChanged(sessionId = newActiveId))
                            }
                        },
                    )
            }
        }

        private fun handleCreateSession() {
            scope.launch {
                sessionService.create(title = SessionTitle(value = ""), projectId = state().filterProjectId)
                    .fold(
                        ifLeft = { error -> dispatch(Msg.Error(text = error.message)) },
                        ifRight = { session ->
                            val item = SessionListStore.SessionItem(
                                id = session.id,
                                title = session.title.value,
                                updatedAt = session.updatedAt.value,
                                projectId = session.projectId,
                            )
                            dispatch(Msg.SessionCreated(item = item))
                        },
                    )
            }
        }

        private fun handleDeleteSession(id: AgentSessionId) {
            scope.launch {
                sessionService.delete(id = id)
                    .fold(
                        ifLeft = { error -> dispatch(Msg.Error(text = error.message)) },
                        ifRight = {
                            val remaining = sessionService.list().getOrElse { emptyList() }
                            val currentActive = state().activeSessionId
                            val newActiveId = if (currentActive == id) {
                                remaining.firstOrNull()?.id
                            } else {
                                currentActive
                            }
                            dispatch(Msg.SessionDeleted(id = id, newActiveId = newActiveId))
                        },
                    )
            }
        }
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<SessionListStore.State, Msg> {
        override fun SessionListStore.State.reduce(msg: Msg): SessionListStore.State =
            when (msg) {
                is Msg.SessionsLoaded -> copy(
                    sessions = msg.sessions,
                    activeSessionId = msg.activeSessionId,
                    errorText = null,
                )
                is Msg.SessionCreated -> copy(
                    sessions = listOf(msg.item) + sessions,
                    activeSessionId = msg.item.id,
                    errorText = null,
                )
                is Msg.SessionDeleted -> copy(
                    sessions = sessions.filter { it.id != msg.id },
                    activeSessionId = msg.newActiveId,
                    errorText = null,
                )
                is Msg.SessionSelected -> copy(
                    activeSessionId = msg.id,
                )
                is Msg.ProjectFilterChanged -> copy(
                    filterProjectId = msg.projectId,
                    showFreeSessions = msg.showFreeSessions,
                )
                is Msg.Error -> copy(errorText = msg.text)
            }
    }
}
