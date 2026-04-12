package com.ai.challenge.conversation.service

import arrow.core.Either
import com.ai.challenge.conversation.model.Branch
import com.ai.challenge.conversation.model.Turn
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.identity.TurnId

/**
 * Domain Service -- [Branch] management within an [AgentSession].
 *
 * Creates branches from existing turns, deletes non-main branches,
 * retrieves branch lists and turns. Validates aggregate invariants:
 * branching must be enabled, main branch cannot be deleted.
 *
 * Contains no own state -- all logic is stateless.
 */
interface BranchService {
    suspend fun create(
        sessionId: AgentSessionId,
        sourceTurnId: TurnId,
        fromBranchId: BranchId,
    ): Either<DomainError, Branch>

    suspend fun delete(branchId: BranchId): Either<DomainError, Unit>

    suspend fun getAll(sessionId: AgentSessionId): Either<DomainError, List<Branch>>

    suspend fun getTurns(branchId: BranchId): Either<DomainError, List<Turn>>
}
