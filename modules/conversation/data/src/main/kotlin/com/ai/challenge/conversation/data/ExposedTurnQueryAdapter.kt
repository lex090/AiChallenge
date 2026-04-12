package com.ai.challenge.conversation.data

import com.ai.challenge.conversation.repository.AgentSessionRepository
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.port.TurnQueryPort
import com.ai.challenge.sharedkernel.vo.TurnSnapshot

/**
 * Adapter -- implements [TurnQueryPort] by delegating to [AgentSessionRepository].
 *
 * Maps Turn (Conversation internal) to [TurnSnapshot] (Shared Kernel).
 * This adapter is the only point where Conversation aggregate internals
 * are projected for cross-context consumption.
 *
 * Invariants:
 * - Returns snapshots in the same order as [AgentSessionRepository.getTurnsByBranch].
 * - Never exposes usage metrics or timestamps -- only identity and message content.
 */
class ExposedTurnQueryAdapter(
    private val repository: AgentSessionRepository,
) : TurnQueryPort {

    override suspend fun getTurnSnapshots(
        sessionId: AgentSessionId,
        branchId: BranchId,
    ): List<TurnSnapshot> {
        val turns = repository.getTurnsByBranch(branchId = branchId)
        return turns.map { turn ->
            TurnSnapshot(
                turnId = turn.id,
                userMessage = turn.userMessage,
                assistantMessage = turn.assistantMessage,
            )
        }
    }
}
