package com.ai.challenge.ui.chat.store

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.model.Cost
import com.ai.challenge.core.usage.model.TokenCount
import com.ai.challenge.core.usage.model.UsageRecord
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store
import java.math.BigDecimal

interface ChatStore : Store<ChatStore.Intent, ChatStore.State, Nothing> {

    sealed interface Intent {
        data class SendMessage(val text: String) : Intent
        data class LoadSession(val sessionId: AgentSessionId) : Intent
        data class CreateBranch(val sourceTurnId: TurnId) : Intent
        data class SwitchBranch(val branchId: BranchId) : Intent
        data class DeleteBranch(val branchId: BranchId) : Intent
        data object LoadBranches : Intent
    }

    data class State(
        val sessionId: AgentSessionId? = null,
        val messages: List<UiMessage> = emptyList(),
        val isLoading: Boolean = false,
        val turnUsage: Map<TurnId, UsageRecord> = emptyMap(),
        val sessionUsage: UsageRecord = UsageRecord(
            promptTokens = TokenCount(value = 0),
            completionTokens = TokenCount(value = 0),
            cachedTokens = TokenCount(value = 0),
            cacheWriteTokens = TokenCount(value = 0),
            reasoningTokens = TokenCount(value = 0),
            totalCost = Cost(value = BigDecimal.ZERO),
            upstreamCost = Cost(value = BigDecimal.ZERO),
            upstreamPromptCost = Cost(value = BigDecimal.ZERO),
            upstreamCompletionsCost = Cost(value = BigDecimal.ZERO),
        ),
        val branches: List<Branch> = emptyList(),
        val activeBranchId: BranchId? = null,
        val isBranchingEnabled: Boolean = false,
    )
}
