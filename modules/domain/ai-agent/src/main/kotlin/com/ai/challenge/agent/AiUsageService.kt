package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.UsageService
import com.ai.challenge.core.usage.model.Cost
import com.ai.challenge.core.usage.model.TokenCount
import com.ai.challenge.core.usage.model.UsageRecord
import java.math.BigDecimal

class AiUsageService(
    private val repository: AgentSessionRepository,
) : UsageService {

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
        turns.map { turn -> turn.usage }.fold(initial = ZERO_USAGE) { acc, record -> acc + record }
    }

    private suspend fun getAllTurns(sessionId: AgentSessionId): List<Turn> {
        val branches = repository.getBranches(sessionId = sessionId)
        return branches.flatMap { branch -> repository.getTurnsByBranch(branchId = branch.id) }
            .distinctBy { turn -> turn.id }
    }

    companion object {
        private val ZERO_USAGE = UsageRecord(
            promptTokens = TokenCount(value = 0),
            completionTokens = TokenCount(value = 0),
            cachedTokens = TokenCount(value = 0),
            cacheWriteTokens = TokenCount(value = 0),
            reasoningTokens = TokenCount(value = 0),
            totalCost = Cost(value = BigDecimal.ZERO),
            upstreamCost = Cost(value = BigDecimal.ZERO),
            upstreamPromptCost = Cost(value = BigDecimal.ZERO),
            upstreamCompletionsCost = Cost(value = BigDecimal.ZERO),
        )
    }
}
