package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.contextmanagement.model.ContextManagementType
import com.ai.challenge.contextmanagement.model.ContextStrategyConfig
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.port.ContextManagerPort
import com.ai.challenge.sharedkernel.vo.ContextModeId
import com.ai.challenge.sharedkernel.vo.MessageContent
import com.ai.challenge.sharedkernel.vo.PreparedContext

/**
 * Adapter -- implements [ContextManagerPort] from the shared kernel.
 *
 * Orchestrates context preparation by mapping [ContextModeId] to
 * [ContextManagementType], selecting the corresponding [ContextStrategy]
 * and [ContextStrategyConfig], and delegating the actual preparation.
 *
 * No longer reads the session -- receives [contextModeId] as a parameter
 * from the calling bounded context (Conversation).
 */
class ContextPreparationAdapter(
    private val strategies: Map<ContextManagementType, ContextStrategy>,
    private val configs: Map<ContextManagementType, ContextStrategyConfig>,
) : ContextManagerPort {

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        contextModeId: ContextModeId,
    ): PreparedContext {
        val type = ContextManagementType.fromModeId(contextModeId = contextModeId)
            ?: error("Unknown context mode: ${contextModeId.value}")
        val strategy = strategies[type] ?: error("No strategy for: $type")
        val config = configs[type] ?: error("No config for: $type")
        return strategy.prepare(sessionId = sessionId, branchId = branchId, newMessage = newMessage, config = config)
    }
}
