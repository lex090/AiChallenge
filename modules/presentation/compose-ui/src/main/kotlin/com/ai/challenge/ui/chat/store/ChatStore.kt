package com.ai.challenge.ui.chat.store

import com.ai.challenge.core.BranchId
import com.ai.challenge.core.BranchTree
import com.ai.challenge.core.ContextStrategyType
import com.ai.challenge.core.CostDetails
import com.ai.challenge.core.Fact
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.TokenDetails
import com.ai.challenge.core.TurnId
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store

interface ChatStore : Store<ChatStore.Intent, ChatStore.State, Nothing> {

    sealed interface Intent {
        data class SendMessage(val text: String) : Intent
        data class LoadSession(val sessionId: SessionId) : Intent
        data class SwitchStrategy(val strategy: ContextStrategyType) : Intent
        data class CreateBranch(val checkpointTurnIndex: Int, val name: String) : Intent
        data class SwitchBranch(val branchId: BranchId) : Intent
        data object LoadFacts : Intent
        data object LoadBranchTree : Intent
    }

    data class State(
        val sessionId: SessionId? = null,
        val messages: List<UiMessage> = emptyList(),
        val isLoading: Boolean = false,
        val turnTokens: Map<TurnId, TokenDetails> = emptyMap(),
        val turnCosts: Map<TurnId, CostDetails> = emptyMap(),
        val sessionTokens: TokenDetails = TokenDetails(),
        val sessionCosts: CostDetails = CostDetails(),
        val currentStrategy: ContextStrategyType = ContextStrategyType.SlidingWindow,
        val facts: List<Fact> = emptyList(),
        val branchTree: BranchTree? = null,
    )
}
