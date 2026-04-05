package com.ai.challenge.ui.chat

import com.ai.challenge.core.Agent
import com.ai.challenge.core.BranchId
import com.ai.challenge.core.ContextStrategyType
import com.ai.challenge.core.SessionId
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
    sessionId: SessionId,
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

    fun onSwitchStrategy(type: ContextStrategyType) {
        store.accept(ChatStore.Intent.SwitchStrategy(type))
    }

    fun onCreateBranch(name: String) {
        store.accept(ChatStore.Intent.CreateBranch(name))
    }

    fun onSwitchBranch(branchId: BranchId) {
        store.accept(ChatStore.Intent.SwitchBranch(branchId))
    }

    fun onSwitchToMain() {
        store.accept(ChatStore.Intent.SwitchToMain)
    }

    fun onLoadFacts() {
        store.accept(ChatStore.Intent.LoadFacts)
    }

    fun onLoadBranches() {
        store.accept(ChatStore.Intent.LoadBranches)
    }
}
