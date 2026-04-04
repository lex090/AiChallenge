package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.agent.Agent
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.TokenUsage
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class ChatStoreFactory(
    private val storeFactory: StoreFactory,
    private val agent: Agent,
    private val sessionManager: AgentSessionManager,
) {
    fun create(): ChatStore =
        object : ChatStore,
            Store<ChatStore.Intent, ChatStore.State, Nothing> by storeFactory.create(
                name = "ChatStore",
                initialState = ChatStore.State(),
                executorFactory = { ExecutorImpl(agent, sessionManager) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class SessionLoaded(
            val sessionId: SessionId,
            val messages: List<UiMessage>,
            val sessionTokens: TokenUsage,
        ) : Msg
        data class UserMessage(val text: String) : Msg
        data class AgentResponseMsg(val text: String, val tokenUsage: TokenUsage) : Msg
        data class Error(val text: String) : Msg
        data object Loading : Msg
        data object LoadingComplete : Msg
    }

    private class ExecutorImpl(
        private val agent: Agent,
        private val sessionManager: AgentSessionManager,
    ) : CoroutineExecutor<ChatStore.Intent, Nothing, ChatStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: ChatStore.Intent) {
            when (intent) {
                is ChatStore.Intent.SendMessage -> handleSendMessage(intent.text)
                is ChatStore.Intent.LoadSession -> handleLoadSession(intent.sessionId)
            }
        }

        private fun handleLoadSession(sessionId: SessionId) {
            scope.launch {
                val history = sessionManager.getHistory(sessionId)
                val messages = history.flatMap { turn ->
                    listOf(
                        UiMessage(text = turn.userMessage, isUser = true, tokenUsage = turn.tokenUsage),
                        UiMessage(text = turn.agentResponse, isUser = false, tokenUsage = turn.tokenUsage),
                    )
                }
                val sessionTokens = history.fold(TokenUsage()) { acc, turn ->
                    acc + turn.tokenUsage
                }
                dispatch(Msg.SessionLoaded(sessionId, messages, sessionTokens))
            }
        }

        private fun handleSendMessage(text: String) {
            val sessionId = state().sessionId ?: return
            dispatch(Msg.UserMessage(text))
            dispatch(Msg.Loading)

            scope.launch {
                when (val result = agent.send(sessionId, text)) {
                    is Either.Right -> dispatch(Msg.AgentResponseMsg(result.value.text, result.value.tokenUsage))
                    is Either.Left -> dispatch(Msg.Error(result.value.message))
                }
                dispatch(Msg.LoadingComplete)

                val session = sessionManager.getSession(sessionId)
                if (session != null && session.title.isEmpty()) {
                    val title = text.take(50)
                    sessionManager.updateTitle(sessionId, title)
                }
            }
        }
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<ChatStore.State, Msg> {
        override fun ChatStore.State.reduce(msg: Msg): ChatStore.State =
            when (msg) {
                is Msg.SessionLoaded -> copy(
                    sessionId = msg.sessionId,
                    messages = msg.messages,
                    sessionTokens = msg.sessionTokens,
                )
                is Msg.UserMessage -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = true),
                )
                is Msg.AgentResponseMsg -> {
                    val lastMsg = messages.lastOrNull()
                    val updatedMessages = if (lastMsg != null) {
                        messages.dropLast(1) + lastMsg.copy(tokenUsage = msg.tokenUsage)
                    } else {
                        messages
                    }
                    copy(
                        messages = updatedMessages + UiMessage(text = msg.text, isUser = false, tokenUsage = msg.tokenUsage),
                        sessionTokens = sessionTokens + msg.tokenUsage,
                    )
                }
                is Msg.Error -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false, isError = true),
                )
                is Msg.Loading -> copy(isLoading = true)
                is Msg.LoadingComplete -> copy(isLoading = false)
            }
    }
}
