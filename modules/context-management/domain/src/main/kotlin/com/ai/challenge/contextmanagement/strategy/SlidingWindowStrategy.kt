package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.contextmanagement.model.ContextStrategyConfig
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.port.TurnQueryPort
import com.ai.challenge.sharedkernel.vo.ContextMessage
import com.ai.challenge.sharedkernel.vo.MessageContent
import com.ai.challenge.sharedkernel.vo.MessageRole
import com.ai.challenge.sharedkernel.vo.PreparedContext

/**
 * Strategy -- sliding window over recent turns.
 *
 * Keeps only the last N turns from the branch history,
 * discarding older ones. Used with [ContextManagementType.SlidingWindow].
 */
class SlidingWindowStrategy(
    private val turnQueryPort: TurnQueryPort,
) : ContextStrategy {

    override suspend fun prepare(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        config: ContextStrategyConfig,
    ): PreparedContext {
        val slidingConfig = config as ContextStrategyConfig.SlidingWindow
        val history = turnQueryPort.getTurnSnapshots(sessionId = sessionId, branchId = branchId)
        val windowed = history.takeLast(n = slidingConfig.windowSize)
        return PreparedContext(
            messages = snapshotsToMessages(snapshots = windowed) + ContextMessage(role = MessageRole.User, content = newMessage),
            compressed = false,
            originalTurnCount = history.size,
            retainedTurnCount = windowed.size,
            summaryCount = 0,
        )
    }
}
