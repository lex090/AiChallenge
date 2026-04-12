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
 * Strategy -- full history passthrough, no compression.
 *
 * Sends all turns from the branch history plus the new message
 * to the LLM without any processing. Used with [ContextManagementType.None].
 */
class PassthroughStrategy(
    private val turnQueryPort: TurnQueryPort,
) : ContextStrategy {

    override suspend fun prepare(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        config: ContextStrategyConfig,
    ): PreparedContext {
        val history = turnQueryPort.getTurnSnapshots(sessionId = sessionId, branchId = branchId)
        return PreparedContext(
            messages = snapshotsToMessages(snapshots = history) + ContextMessage(role = MessageRole.User, content = newMessage),
            compressed = false,
            originalTurnCount = history.size,
            retainedTurnCount = history.size,
            summaryCount = 0,
        )
    }
}
