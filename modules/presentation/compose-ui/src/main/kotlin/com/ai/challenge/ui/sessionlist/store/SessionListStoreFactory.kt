package com.ai.challenge.ui.sessionlist.store

import arrow.core.Either
import com.ai.challenge.core.agent.SessionManager
import com.ai.challenge.core.session.AgentSessionId
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class SessionListStoreFactory(
    private val storeFactory: StoreFactory,
    private val sessionManager: SessionManager,
) {
    fun create(): SessionListStore =
        object : SessionListStore,
            Store<SessionListStore.Intent, SessionListStore.State, Nothing> by storeFactory.create(
                name = "SessionListStore",
                initialState = SessionListStore.State(),
                executorFactory = { ExecutorImpl(sessionManager) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class SessionsLoaded(val sessions: List<SessionListStore.SessionItem>, val activeSessionId: AgentSessionId?) : Msg
        data class SessionCreated(val item: SessionListStore.SessionItem) : Msg
        data class SessionDeleted(val id: AgentSessionId, val newActiveId: AgentSessionId?) : Msg
        data class SessionSelected(val id: AgentSessionId) : Msg
    }

    private class ExecutorImpl(
        private val sessionManager: SessionManager,
    ) : CoroutineExecutor<SessionListStore.Intent, Nothing, SessionListStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: SessionListStore.Intent) {
            when (intent) {
                is SessionListStore.Intent.LoadSessions -> handleLoadSessions()
                is SessionListStore.Intent.CreateSession -> handleCreateSession()
                is SessionListStore.Intent.DeleteSession -> handleDeleteSession(intent.id)
                is SessionListStore.Intent.SelectSession -> dispatch(Msg.SessionSelected(intent.id))
            }
        }

        private fun handleLoadSessions() {
            scope.launch {
                when (val result = sessionManager.listSessions()) {
                    is Either.Right -> {
                        val sessions = result.value.map { session ->
                            SessionListStore.SessionItem(
                                id = session.id,
                                title = session.title,
                                updatedAt = session.updatedAt,
                            )
                        }
                        dispatch(Msg.SessionsLoaded(sessions, activeSessionId = state().activeSessionId))
                    }
                    is Either.Left -> {}
                }
            }
        }

        private fun handleCreateSession() {
            scope.launch {
                when (val idResult = sessionManager.createSession(title = "")) {
                    is Either.Right -> {
                        when (val sessionResult = sessionManager.getSession(id = idResult.value)) {
                            is Either.Right -> {
                                val session = sessionResult.value
                                val item = SessionListStore.SessionItem(
                                    id = session.id,
                                    title = session.title,
                                    updatedAt = session.updatedAt,
                                )
                                dispatch(Msg.SessionCreated(item))
                            }
                            is Either.Left -> {}
                        }
                    }
                    is Either.Left -> {}
                }
            }
        }

        private fun handleDeleteSession(id: AgentSessionId) {
            scope.launch {
                when (sessionManager.deleteSession(id = id)) {
                    is Either.Right -> {
                        when (val remaining = sessionManager.listSessions()) {
                            is Either.Right -> {
                                val currentActive = state().activeSessionId
                                val newActiveId = if (currentActive == id) {
                                    remaining.value.firstOrNull()?.id
                                } else {
                                    currentActive
                                }
                                dispatch(Msg.SessionDeleted(id = id, newActiveId = newActiveId))
                            }
                            is Either.Left -> dispatch(Msg.SessionDeleted(id = id, newActiveId = null))
                        }
                    }
                    is Either.Left -> {}
                }
            }
        }
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<SessionListStore.State, Msg> {
        override fun SessionListStore.State.reduce(msg: Msg): SessionListStore.State =
            when (msg) {
                is Msg.SessionsLoaded -> copy(
                    sessions = msg.sessions,
                    activeSessionId = msg.activeSessionId,
                )
                is Msg.SessionCreated -> copy(
                    sessions = listOf(msg.item) + sessions,
                    activeSessionId = msg.item.id,
                )
                is Msg.SessionDeleted -> copy(
                    sessions = sessions.filter { it.id != msg.id },
                    activeSessionId = msg.newActiveId,
                )
                is Msg.SessionSelected -> copy(
                    activeSessionId = msg.id,
                )
            }
    }
}
