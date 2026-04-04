package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.agent.Agent
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class ChatStoreFactory(
    private val storeFactory: StoreFactory,
    private val agent: Agent,
) {
    fun create(): ChatStore =
        object : ChatStore,
            Store<ChatStore.Intent, ChatStore.State, Nothing> by storeFactory.create(
                name = "ChatStore",
                initialState = ChatStore.State(),
                executorFactory = { ExecutorImpl(agent) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class UserMessage(val text: String) : Msg
        data class AgentResponse(val text: String) : Msg
        data class Error(val text: String) : Msg
        data object Loading : Msg
        data object LoadingComplete : Msg
    }

    private class ExecutorImpl(
        private val agent: Agent,
    ) : CoroutineExecutor<ChatStore.Intent, Nothing, ChatStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: ChatStore.Intent) {
            when (intent) {
                is ChatStore.Intent.SendMessage -> handleSendMessage(intent.text)
            }
        }

        private fun handleSendMessage(text: String) {
            dispatch(Msg.UserMessage(text))
            dispatch(Msg.Loading)

            scope.launch {
                when (val result = agent.send(text)) {
                    is Either.Right -> dispatch(Msg.AgentResponse(result.value))
                    is Either.Left -> dispatch(Msg.Error(result.value.message))
                }
                dispatch(Msg.LoadingComplete)
            }
        }
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<ChatStore.State, Msg> {
        override fun ChatStore.State.reduce(msg: Msg): ChatStore.State =
            when (msg) {
                is Msg.UserMessage -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = true),
                )
                is Msg.AgentResponse -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false),
                )
                is Msg.Error -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false, isError = true),
                )
                is Msg.Loading -> copy(isLoading = true)
                is Msg.LoadingComplete -> copy(isLoading = false)
            }
    }
}
