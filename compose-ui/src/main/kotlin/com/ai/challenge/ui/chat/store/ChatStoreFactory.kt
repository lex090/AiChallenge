package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.agent.Agent
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.RequestMetrics
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.TurnId
import com.ai.challenge.session.UsageManager
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class ChatStoreFactory(
    private val storeFactory: StoreFactory,
    private val agent: Agent,
    private val sessionManager: AgentSessionManager,
    private val usageManager: UsageManager,
) {
    fun create(): ChatStore =
        object : ChatStore,
            Store<ChatStore.Intent, ChatStore.State, Nothing> by storeFactory.create(
                name = "ChatStore",
                initialState = ChatStore.State(),
                executorFactory = { ExecutorImpl(agent, sessionManager, usageManager) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class SessionLoaded(
            val sessionId: SessionId,
            val messages: List<UiMessage>,
            val turnMetrics: Map<TurnId, RequestMetrics>,
            val sessionMetrics: RequestMetrics,
        ) : Msg
        data class UserMessage(val text: String) : Msg
        data class AgentResponseMsg(
            val text: String,
            val turnId: TurnId,
            val metrics: RequestMetrics,
        ) : Msg
        data class Error(val text: String) : Msg
        data object Loading : Msg
        data object LoadingComplete : Msg
    }

    private class ExecutorImpl(
        private val agent: Agent,
        private val sessionManager: AgentSessionManager,
        private val usageManager: UsageManager,
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
                        UiMessage(text = turn.userMessage, isUser = true, turnId = turn.id),
                        UiMessage(text = turn.agentResponse, isUser = false, turnId = turn.id),
                    )
                }
                val turnMetrics = usageManager.getBySession(sessionId)
                val sessionMetrics = turnMetrics.values.fold(RequestMetrics()) { acc, m -> acc + m }
                dispatch(Msg.SessionLoaded(sessionId, messages, turnMetrics, sessionMetrics))
            }
        }

        private fun handleSendMessage(text: String) {
            val sessionId = state().sessionId ?: return
            dispatch(Msg.UserMessage(text))
            dispatch(Msg.Loading)

            scope.launch {
                when (val result = agent.send(sessionId, text)) {
                    is Either.Right -> dispatch(
                        Msg.AgentResponseMsg(
                            text = result.value.text,
                            turnId = result.value.turnId,
                            metrics = result.value.metrics,
                        )
                    )
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
                    turnMetrics = msg.turnMetrics,
                    sessionMetrics = msg.sessionMetrics,
                )
                is Msg.UserMessage -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = true),
                )
                is Msg.AgentResponseMsg -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false, turnId = msg.turnId),
                    turnMetrics = turnMetrics + (msg.turnId to msg.metrics),
                    sessionMetrics = sessionMetrics + msg.metrics,
                )
                is Msg.Error -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false, isError = true),
                )
                is Msg.Loading -> copy(isLoading = true)
                is Msg.LoadingComplete -> copy(isLoading = false)
            }
    }
}
