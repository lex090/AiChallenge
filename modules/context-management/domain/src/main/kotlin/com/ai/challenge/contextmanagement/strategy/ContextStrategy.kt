package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.contextmanagement.model.ContextStrategyConfig
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.vo.MessageContent
import com.ai.challenge.sharedkernel.vo.PreparedContext

/**
 * Strategy -- prepares conversation context for an LLM call.
 *
 * Each implementation handles one [ContextStrategyConfig] variant.
 * The [ContextPreparationAdapter] orchestrator selects the correct
 * strategy based on the session's [ContextManagementType].
 */
interface ContextStrategy {
    suspend fun prepare(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        config: ContextStrategyConfig,
    ): PreparedContext
}
