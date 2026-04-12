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
 * Strategy -- branch-scoped passthrough.
 *
 * Sends all turns from the current branch plus the new message
 * to the LLM. Used with [ContextManagementType.Branching].
 */
class BranchingContextManager(
    private val turnQueryPort: TurnQueryPort,
) : ContextStrategy {

    override suspend fun prepare(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        config: ContextStrategyConfig,
    ): PreparedContext {
        val snapshots = turnQueryPort.getTurnSnapshots(sessionId = sessionId, branchId = branchId)
        val messages = snapshotsToMessages(snapshots = snapshots) +
            ContextMessage(role = MessageRole.User, content = newMessage)
        return PreparedContext(
            messages = messages,
            compressed = false,
            originalTurnCount = snapshots.size,
            retainedTurnCount = snapshots.size,
            summaryCount = 0,
        )
    }
}
