package com.ai.challenge.ui.chat.store

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.cost.CostDetails
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.token.TokenDetails
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store

interface ChatStore : Store<ChatStore.Intent, ChatStore.State, Nothing> {

    sealed interface Intent {
        data class SendMessage(val text: String) : Intent
        data class LoadSession(val sessionId: AgentSessionId) : Intent
        data class CreateBranch(val name: String, val parentTurnId: TurnId) : Intent
        data class SwitchBranch(val branchId: BranchId) : Intent
        data class DeleteBranch(val branchId: BranchId) : Intent
        data object LoadBranches : Intent
    }

    data class State(
        val sessionId: AgentSessionId? = null,
        val messages: List<UiMessage> = emptyList(),
        val isLoading: Boolean = false,
        val turnTokens: Map<TurnId, TokenDetails> = emptyMap(),
        val turnCosts: Map<TurnId, CostDetails> = emptyMap(),
        val sessionTokens: TokenDetails = TokenDetails(promptTokens = 0, completionTokens = 0, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0),
        val sessionCosts: CostDetails = CostDetails(totalCost = 0.0, upstreamCost = 0.0, upstreamPromptCost = 0.0, upstreamCompletionsCost = 0.0),
        val branches: List<Branch> = emptyList(),
        val activeBranch: Branch? = null,
        val isBranchingEnabled: Boolean = false,
        val branchParentMap: Map<BranchId, BranchId?> = emptyMap(),
    )
}
