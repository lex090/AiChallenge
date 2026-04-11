package com.ai.challenge.ui.settings.store

import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSessionId
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class SessionSettingsStoreFactory(
    private val storeFactory: StoreFactory,
    private val sessionService: SessionService,
) {
    fun create(): SessionSettingsStore =
        object : SessionSettingsStore,
            Store<SessionSettingsStore.Intent, SessionSettingsStore.State, Nothing> by storeFactory.create(
                name = "SessionSettingsStore",
                initialState = SessionSettingsStore.State(
                    sessionId = null,
                    currentType = ContextManagementType.None,
                    isLoading = false,
                    errorText = null,
                ),
                executorFactory = { ExecutorImpl(sessionService = sessionService) },
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
        data class Error(val text: String) : Msg
    }

    private class ExecutorImpl(
        private val sessionService: SessionService,
    ) : CoroutineExecutor<SessionSettingsStore.Intent, Nothing, SessionSettingsStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: SessionSettingsStore.Intent) {
            when (intent) {
                is SessionSettingsStore.Intent.LoadSettings -> handleLoadSettings(sessionId = intent.sessionId)
                is SessionSettingsStore.Intent.ChangeContextManagementType -> handleChangeType(type = intent.type)
            }
        }

        private fun handleLoadSettings(sessionId: AgentSessionId) {
            dispatch(Msg.Loading)
            scope.launch {
                sessionService.get(id = sessionId)
                    .fold(
                        ifLeft = { dispatch(Msg.SettingsLoaded(sessionId = sessionId, type = ContextManagementType.None)) },
                        ifRight = { session -> dispatch(Msg.SettingsLoaded(sessionId = sessionId, type = session.contextManagementType)) },
                    )
                dispatch(Msg.LoadingComplete)
            }
        }

        private fun handleChangeType(type: ContextManagementType) {
            val sessionId = state().sessionId ?: return
            dispatch(Msg.Loading)
            scope.launch {
                sessionService.updateContextManagementType(id = sessionId, type = type)
                    .fold(
                        ifLeft = { error -> dispatch(Msg.Error(text = error.message)) },
                        ifRight = { dispatch(Msg.TypeChanged(type = type)) },
                    )
                dispatch(Msg.LoadingComplete)
            }
        }
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<SessionSettingsStore.State, Msg> {
        override fun SessionSettingsStore.State.reduce(msg: Msg): SessionSettingsStore.State =
            when (msg) {
                is Msg.SettingsLoaded -> copy(sessionId = msg.sessionId, currentType = msg.type, errorText = null)
                is Msg.TypeChanged -> copy(currentType = msg.type, errorText = null)
                is Msg.Loading -> copy(isLoading = true)
                is Msg.LoadingComplete -> copy(isLoading = false)
                is Msg.Error -> copy(errorText = msg.text)
            }
    }
}
