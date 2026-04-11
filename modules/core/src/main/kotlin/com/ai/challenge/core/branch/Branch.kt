package com.ai.challenge.core.branch

import arrow.core.Either
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.turn.TurnId

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
