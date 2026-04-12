package com.ai.challenge.conversation.impl

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.conversation.model.Branch
import com.ai.challenge.conversation.model.Turn
import com.ai.challenge.conversation.model.TurnSequence
import com.ai.challenge.conversation.repository.AgentSessionRepository
import com.ai.challenge.conversation.service.BranchService
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.identity.TurnId
import com.ai.challenge.sharedkernel.vo.CreatedAt
import kotlin.time.Clock

/**
 * Domain Service implementation -- [Branch] management within an [AgentSession].
 *
 * Creates branches from existing turns (trunk up to source turn),
 * deletes non-main branches, retrieves branch lists and turns.
 * Validates aggregate invariants before mutations.
 */
class AiBranchService(
    private val repository: AgentSessionRepository,
) : BranchService {

    override suspend fun create(
        sessionId: AgentSessionId,
        sourceTurnId: TurnId,
        fromBranchId: BranchId,
    ): Either<DomainError, Branch> = either {
        repository.get(id = sessionId)
            ?: raise(DomainError.SessionNotFound(id = sessionId))

        val fromBranch = repository.getBranch(branchId = fromBranchId)
            ?: raise(DomainError.BranchNotFound(id = fromBranchId))

        val trunkSequence = fromBranch.turnSequence.trunkUpTo(turnId = sourceTurnId)

        val branch = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            sourceTurnId = sourceTurnId,
            turnSequence = trunkSequence,
            createdAt = CreatedAt(value = Clock.System.now()),
        )
        repository.createBranch(branch = branch)
    }

    override suspend fun delete(branchId: BranchId): Either<DomainError, Unit> = either {
        val branch = repository.getBranch(branchId = branchId)
            ?: raise(DomainError.BranchNotFound(id = branchId))

        val session = repository.get(id = branch.sessionId)
            ?: raise(DomainError.SessionNotFound(id = branch.sessionId))

        session.ensureBranchDeletable(branch = branch).bind()

        repository.deleteTurnsByBranch(branchId = branchId)
        repository.deleteBranch(branchId = branchId)
    }

    override suspend fun getAll(sessionId: AgentSessionId): Either<DomainError, List<Branch>> =
        Either.Right(value = repository.getBranches(sessionId = sessionId))

    override suspend fun getTurns(branchId: BranchId): Either<DomainError, List<Turn>> = either {
        repository.getBranch(branchId = branchId)
            ?: raise(DomainError.BranchNotFound(id = branchId))
        repository.getTurnsByBranch(branchId = branchId)
    }
}
