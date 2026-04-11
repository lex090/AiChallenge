package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.ContextStrategyConfig
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.context.PreparedContext
import com.ai.challenge.core.session.AgentSessionId

class BranchingContextManager(
    private val repository: AgentSessionRepository,
) : ContextStrategy {

    override suspend fun prepare(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        config: ContextStrategyConfig,
    ): PreparedContext {
        val turns = repository.getTurnsByBranch(branchId = branchId)
        val messages = turnsToMessages(turns = turns) +
            ContextMessage(role = MessageRole.User, content = newMessage)
        return PreparedContext(
            messages = messages,
            compressed = false,
            originalTurnCount = turns.size,
            retainedTurnCount = turns.size,
            summaryCount = 0,
        )
    }
}
