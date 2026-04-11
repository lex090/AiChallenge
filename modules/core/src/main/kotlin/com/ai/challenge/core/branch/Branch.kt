package com.ai.challenge.core.branch

import arrow.core.Either
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.turn.TurnId

/**
 * Entity — conversation branch within aggregate [AgentSession].
 *
 * Has stable identity [BranchId], but is NOT an independent
 * Aggregate Root — access only through [AgentSessionRepository].
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

    fun ensureDeletable(): Either<DomainError, Unit> =
        if (isMain) Either.Left(value = DomainError.MainBranchCannotBeDeleted(sessionId = sessionId))
        else Either.Right(value = Unit)
}
