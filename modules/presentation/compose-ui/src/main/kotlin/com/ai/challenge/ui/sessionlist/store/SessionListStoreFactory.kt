package com.ai.challenge.ui.sessionlist.store

import com.ai.challenge.core.Agent
import com.ai.challenge.core.SessionId
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class SessionListStoreFactory(
    private val storeFactory: StoreFactory,
    private val agent: Agent,
) {
    fun create(): SessionListStore =
        object : SessionListStore,
            Store<SessionListStore.Intent, SessionListStore.State, Nothing> by storeFactory.create(
                name = "SessionListStore",
                initialState = SessionListStore.State(),
                executorFactory = { ExecutorImpl(agent) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class SessionsLoaded(val sessions: List<SessionListStore.SessionItem>, val activeSessionId: SessionId?) : Msg
        data class SessionCreated(val item: SessionListStore.SessionItem) : Msg
        data class SessionDeleted(val id: SessionId, val newActiveId: SessionId?) : Msg
        data class SessionSelected(val id: SessionId) : Msg
    }

    private class ExecutorImpl(
        private val agent: Agent,
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
                val sessions = agent.listSessions().map { session ->
                    SessionListStore.SessionItem(
                        id = session.id,
                        title = session.title,
                        updatedAt = session.updatedAt,
                    )
                }
                dispatch(Msg.SessionsLoaded(sessions, activeSessionId = state().activeSessionId))
            }
        }

        private fun handleCreateSession() {
            scope.launch {
                val id = agent.createSession()
                val session = agent.getSession(id)!!
                val item = SessionListStore.SessionItem(
                    id = session.id,
                    title = session.title,
                    updatedAt = session.updatedAt,
                )
                dispatch(Msg.SessionCreated(item))
            }
        }

        private fun handleDeleteSession(id: SessionId) {
            scope.launch {
                agent.deleteSession(id)
                val remaining = agent.listSessions()
                val currentActive = state().activeSessionId
                val newActiveId = if (currentActive == id) {
                    remaining.firstOrNull()?.id
                } else {
                    currentActive
                }
                dispatch(Msg.SessionDeleted(id, newActiveId))
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
