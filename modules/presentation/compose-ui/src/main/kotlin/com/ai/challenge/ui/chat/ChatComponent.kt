package com.ai.challenge.ui.chat

import com.ai.challenge.core.agent.Agent
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
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
        ChatStoreFactory(storeFactory = storeFactory, agent = agent).create()
    }

    init {
        store.accept(ChatStore.Intent.LoadSession(sessionId = sessionId))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ChatStore.State> = store.stateFlow

    fun onSendMessage(text: String) {
        store.accept(ChatStore.Intent.SendMessage(text = text))
    }

    fun onCreateBranch(name: String, parentTurnId: TurnId) {
        store.accept(ChatStore.Intent.CreateBranch(name = name, parentTurnId = parentTurnId))
    }

    fun onSwitchBranch(branchId: BranchId) {
        store.accept(ChatStore.Intent.SwitchBranch(branchId = branchId))
    }

    fun onDeleteBranch(branchId: BranchId) {
        store.accept(ChatStore.Intent.DeleteBranch(branchId = branchId))
    }

    fun refreshBranches() {
        store.accept(ChatStore.Intent.LoadBranches)
    }
}
