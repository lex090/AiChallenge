package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.TurnSequence
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.BranchService
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import kotlin.time.Clock

class AiBranchService(
    private val repository: AgentSessionRepository,
) : BranchService {

    override suspend fun create(
        sessionId: AgentSessionId,
        sourceTurnId: TurnId,
        fromBranchId: BranchId,
    ): Either<DomainError, Branch> = either {
        val session = repository.get(id = sessionId)
            ?: raise(DomainError.SessionNotFound(id = sessionId))

        if (session.contextManagementType !is ContextManagementType.Branching) {
            raise(DomainError.BranchingNotEnabled(sessionId = sessionId))
        }

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

        branch.ensureDeletable().bind()

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
