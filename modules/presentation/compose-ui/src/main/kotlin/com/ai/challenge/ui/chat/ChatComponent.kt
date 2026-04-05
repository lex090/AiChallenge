package com.ai.challenge.ui.chat

import com.ai.challenge.core.agent.Agent
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.ui.chat.store.ChatStore
import com.ai.challenge.ui.chat.store.ChatStoreFactory
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow

class ChatComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    agent: Agent,
    sessionId: AgentSessionId,
) : ComponentContext by componentContext {

    private val store = instanceKeeper.getStore {
        ChatStoreFactory(storeFactory, agent).create()
    }

    init {
        store.accept(ChatStore.Intent.LoadSession(sessionId))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ChatStore.State> = store.stateFlow

    fun onSendMessage(text: String) {
        store.accept(ChatStore.Intent.SendMessage(text))
    }
}
