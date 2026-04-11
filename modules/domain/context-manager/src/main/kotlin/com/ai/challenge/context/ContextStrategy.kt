package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextStrategyConfig
import com.ai.challenge.core.context.PreparedContext
import com.ai.challenge.core.session.AgentSessionId

/**
 * Strategy — prepares conversation context for an LLM call.
 *
 * Each implementation handles one [ContextStrategyConfig] variant.
 * The [ContextPreparationService] orchestrator selects the correct
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
