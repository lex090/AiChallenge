package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.ContextStrategyConfig
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.context.PreparedContext
import com.ai.challenge.core.session.AgentSessionId

class SlidingWindowStrategy(
    private val repository: AgentSessionRepository,
) : ContextStrategy {

    override suspend fun prepare(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        config: ContextStrategyConfig,
    ): PreparedContext {
        val slidingConfig = config as ContextStrategyConfig.SlidingWindow
        val history = repository.getTurnsByBranch(branchId = branchId)
        val windowed = history.takeLast(n = slidingConfig.windowSize)
        return PreparedContext(
            messages = turnsToMessages(turns = windowed) + ContextMessage(role = MessageRole.User, content = newMessage),
            compressed = false,
            originalTurnCount = history.size,
            retainedTurnCount = windowed.size,
            summaryCount = 0,
        )
    }
}
