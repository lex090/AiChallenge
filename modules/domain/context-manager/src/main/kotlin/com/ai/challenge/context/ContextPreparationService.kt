package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.ContextStrategyConfig
import com.ai.challenge.core.context.PreparedContext
import com.ai.challenge.core.session.AgentSessionId

class ContextPreparationService(
    private val strategies: Map<ContextManagementType, ContextStrategy>,
    private val configs: Map<ContextManagementType, ContextStrategyConfig>,
    private val repository: AgentSessionRepository,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
    ): PreparedContext {
        val session = repository.get(id = sessionId)
            ?: error("Session not found: ${sessionId.value}")
        val type = session.contextManagementType
        val strategy = strategies[type] ?: error("No strategy for: $type")
        val config = configs[type] ?: error("No config for: $type")
        return strategy.prepare(sessionId = sessionId, branchId = branchId, newMessage = newMessage, config = config)
    }
}
