package com.ai.challenge.ui.chat

import com.ai.challenge.core.chat.BranchService
import com.ai.challenge.core.chat.ChatService
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.UsageService
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
    chatService: ChatService,
    sessionService: SessionService,
    usageService: UsageService,
    branchService: BranchService,
    sessionId: AgentSessionId,
) : ComponentContext by componentContext {

    private val store = instanceKeeper.getStore {
        ChatStoreFactory(
            storeFactory = storeFactory,
            chatService = chatService,
            sessionService = sessionService,
            usageService = usageService,
            branchService = branchService,
        ).create()
    }

    init {
        store.accept(ChatStore.Intent.LoadSession(sessionId = sessionId))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ChatStore.State> = store.stateFlow

    fun onSendMessage(text: String) {
        store.accept(ChatStore.Intent.SendMessage(text = text))
    }

    fun onCreateBranch(sourceTurnId: TurnId) {
        store.accept(ChatStore.Intent.CreateBranch(sourceTurnId = sourceTurnId))
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
