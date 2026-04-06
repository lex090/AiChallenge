package com.ai.challenge.ui.settings.store

import arrow.core.Either
import com.ai.challenge.core.agent.Agent
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSessionId
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class SessionSettingsStoreFactory(
    private val storeFactory: StoreFactory,
    private val agent: Agent,
) {
    fun create(): SessionSettingsStore =
        object : SessionSettingsStore,
            Store<SessionSettingsStore.Intent, SessionSettingsStore.State, Nothing> by storeFactory.create(
                name = "SessionSettingsStore",
                initialState = SessionSettingsStore.State(
                    sessionId = null,
                    currentType = ContextManagementType.None,
                    isLoading = false,
                ),
                executorFactory = { ExecutorImpl(agent) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class SettingsLoaded(
            val sessionId: AgentSessionId,
            val type: ContextManagementType,
        ) : Msg
        data class TypeChanged(val type: ContextManagementType) : Msg
        data object Loading : Msg
        data object LoadingComplete : Msg
    }

    private class ExecutorImpl(
        private val agent: Agent,
    ) : CoroutineExecutor<SessionSettingsStore.Intent, Nothing, SessionSettingsStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: SessionSettingsStore.Intent) {
            when (intent) {
                is SessionSettingsStore.Intent.LoadSettings -> handleLoadSettings(intent.sessionId)
                is SessionSettingsStore.Intent.ChangeContextManagementType -> handleChangeType(intent.type)
            }
        }

        private fun handleLoadSettings(sessionId: AgentSessionId) {
            dispatch(Msg.Loading)
            scope.launch {
                when (val result = agent.getContextManagementType(sessionId)) {
                    is Either.Right -> dispatch(Msg.SettingsLoaded(sessionId, result.value))
                    is Either.Left -> dispatch(Msg.SettingsLoaded(sessionId, ContextManagementType.None))
                }
                dispatch(Msg.LoadingComplete)
            }
        }

        private fun handleChangeType(type: ContextManagementType) {
            val sessionId = state().sessionId ?: return
            dispatch(Msg.Loading)
            scope.launch {
                when (agent.updateContextManagementType(sessionId, type)) {
                    is Either.Right -> dispatch(Msg.TypeChanged(type))
                    is Either.Left -> {}
                }
                dispatch(Msg.LoadingComplete)
            }
        }
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<SessionSettingsStore.State, Msg> {
        override fun SessionSettingsStore.State.reduce(msg: Msg): SessionSettingsStore.State =
            when (msg) {
                is Msg.SettingsLoaded -> copy(sessionId = msg.sessionId, currentType = msg.type)
                is Msg.TypeChanged -> copy(currentType = msg.type)
                is Msg.Loading -> copy(isLoading = true)
                is Msg.LoadingComplete -> copy(isLoading = false)
            }
    }
}
