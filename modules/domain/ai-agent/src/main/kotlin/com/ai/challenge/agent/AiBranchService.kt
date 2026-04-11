package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.BranchService
import com.ai.challenge.core.chat.model.BranchName
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
        name: BranchName,
        parentTurnId: TurnId,
        fromBranchId: BranchId,
    ): Either<DomainError, Branch> = either {
        val session = repository.get(id = sessionId)
            ?: raise(DomainError.SessionNotFound(id = sessionId))

        if (session.contextManagementType !is ContextManagementType.Branching) {
            raise(DomainError.BranchingNotEnabled(sessionId = sessionId))
        }

        val fromBranch = repository.getBranch(branchId = fromBranchId)
            ?: raise(DomainError.BranchNotFound(id = fromBranchId))

        val parentTurnIds = fromBranch.turnIds
        val cutIndex = parentTurnIds.indexOf(element = parentTurnId)
        val trunkTurnIds = if (cutIndex >= 0) {
            parentTurnIds.subList(fromIndex = 0, toIndex = cutIndex + 1)
        } else {
            parentTurnIds
        }

        val branch = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            parentId = fromBranchId,
            name = name,
            turnIds = trunkTurnIds,
            createdAt = CreatedAt(value = Clock.System.now()),
        )
        repository.createBranch(branch = branch)
    }

    override suspend fun delete(branchId: BranchId): Either<DomainError, Unit> = either {
        val branch = repository.getBranch(branchId = branchId)
            ?: raise(DomainError.BranchNotFound(id = branchId))

        if (branch.isMain) {
            raise(DomainError.MainBranchCannotBeDeleted(sessionId = branch.sessionId))
        }

        cascadeDeleteBranch(branchId = branchId, sessionId = branch.sessionId)
    }

    override suspend fun switch(
        sessionId: AgentSessionId,
        branchId: BranchId,
    ): Either<DomainError, Unit> = either {
        val session = repository.get(id = sessionId)
            ?: raise(DomainError.SessionNotFound(id = sessionId))

        val branch = repository.getBranch(branchId = branchId)
            ?: raise(DomainError.BranchNotFound(id = branchId))

        if (branch.sessionId != sessionId) {
            raise(DomainError.BranchNotOwnedBySession(branchId = branchId, sessionId = sessionId))
        }

        repository.update(session = session.withActiveBranch(branchId = branchId))
        Unit
    }

    override suspend fun getAll(sessionId: AgentSessionId): Either<DomainError, List<Branch>> =
        Either.Right(value = repository.getBranches(sessionId = sessionId))

    override suspend fun getActive(sessionId: AgentSessionId): Either<DomainError, Branch> = either {
        val session = repository.get(id = sessionId)
            ?: raise(DomainError.SessionNotFound(id = sessionId))

        repository.getBranch(branchId = session.activeBranchId)
            ?: raise(DomainError.BranchNotFound(id = session.activeBranchId))
    }

    override suspend fun getActiveTurns(sessionId: AgentSessionId): Either<DomainError, List<Turn>> = either {
        val session = repository.get(id = sessionId)
            ?: raise(DomainError.SessionNotFound(id = sessionId))

        if (session.contextManagementType !is ContextManagementType.Branching) {
            return@either repository.getTurns(sessionId = sessionId, limit = null)
        }

        repository.getTurnsByBranch(branchId = session.activeBranchId)
    }

    override suspend fun getParentMap(sessionId: AgentSessionId): Either<DomainError, Map<BranchId, BranchId?>> = either {
        val branches = repository.getBranches(sessionId = sessionId)
        branches.associate { it.id to it.parentId }
    }

    private suspend fun cascadeDeleteBranch(branchId: BranchId, sessionId: AgentSessionId) {
        val allBranches = repository.getBranches(sessionId = sessionId)
        val childBranches = allBranches.filter { it.parentId == branchId }

        for (child in childBranches) {
            cascadeDeleteBranch(branchId = child.id, sessionId = sessionId)
        }

        val session = repository.get(id = sessionId)
        val wasActive = session?.activeBranchId == branchId

        repository.deleteTurnsByBranch(branchId = branchId)
        repository.deleteBranch(branchId = branchId)

        if (wasActive) {
            val mainBranch = repository.getMainBranch(sessionId = sessionId)
            if (mainBranch != null && session != null) {
                repository.update(session = session.withActiveBranch(branchId = mainBranch.id))
            }
        }
    }
}
