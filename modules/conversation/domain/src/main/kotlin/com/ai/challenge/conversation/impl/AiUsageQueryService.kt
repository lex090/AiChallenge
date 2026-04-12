package com.ai.challenge.conversation.impl

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.conversation.model.Turn
import com.ai.challenge.conversation.model.UsageRecord
import com.ai.challenge.conversation.repository.AgentSessionRepository
import com.ai.challenge.conversation.service.UsageQueryService
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.TurnId

/**
 * Domain Service implementation -- read-only usage metrics aggregation.
 *
 * Queries [Turn] data through [AgentSessionRepository] and aggregates
 * [UsageRecord] by turn, session, or session total.
 */
class AiUsageQueryService(
    private val repository: AgentSessionRepository,
) : UsageQueryService {

    override suspend fun getByTurn(turnId: TurnId): Either<DomainError, UsageRecord> = either {
        val turn = repository.getTurn(turnId = turnId)
            ?: raise(DomainError.TurnNotFound(id = turnId))
        turn.usage
    }

    override suspend fun getBySession(sessionId: AgentSessionId): Either<DomainError, Map<TurnId, UsageRecord>> = either {
        val turns = getAllTurns(sessionId = sessionId)
        turns.associate { turn -> turn.id to turn.usage }
    }

    override suspend fun getSessionTotal(sessionId: AgentSessionId): Either<DomainError, UsageRecord> = either {
        val turns = getAllTurns(sessionId = sessionId)
        turns.map { turn -> turn.usage }.fold(initial = UsageRecord.ZERO) { acc, record -> acc + record }
    }

    private suspend fun getAllTurns(sessionId: AgentSessionId): List<Turn> {
        val branches = repository.getBranches(sessionId = sessionId)
        return branches.flatMap { branch -> repository.getTurnsByBranch(branchId = branch.id) }
            .distinctBy { turn -> turn.id }
    }
}
