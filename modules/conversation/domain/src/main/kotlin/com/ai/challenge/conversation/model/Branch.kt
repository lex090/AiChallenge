package com.ai.challenge.conversation.model

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.identity.TurnId
import com.ai.challenge.sharedkernel.vo.CreatedAt

/**
 * Entity -- conversation branch within aggregate [AgentSession].
 *
 * Has stable identity [BranchId], but is NOT an independent
 * Aggregate Root -- access only through [AgentSessionRepository].
 *
 * Lifecycle: main branch created with session ([sourceTurnId] == null).
 * Additional branches created when user branches from an existing [Turn].
 * Deleted cascadingly when session is deleted.
 *
 * Invariants:
 * - [isMain] == true when [sourceTurnId] == null
 * - [turnSequence] is ordered chronologically and append-only
 * - [sourceTurnId] references an existing [Turn] in the parent branch
 *
 * Not a separate Aggregate Root because:
 * - Cannot exist without session
 * - "Main branch not deletable" is an [AgentSession] aggregate invariant
 * - All operations go through [AgentSessionRepository] (one repo per aggregate)
 */
data class Branch(
    val id: BranchId,
    val sessionId: AgentSessionId,
    val sourceTurnId: TurnId?,
    val turnSequence: TurnSequence,
    val createdAt: CreatedAt,
) {
    val isMain: Boolean get() = sourceTurnId == null
}
