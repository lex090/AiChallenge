package com.ai.challenge.core.usage

import arrow.core.Either
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.model.UsageRecord

/**
 * Domain Service — read-only usage metrics aggregation.
 *
 * Queries [Turn] data and aggregates [UsageRecord]
 * by turn, session, or session total.
 *
 * Read-only service — does not mutate any data.
 * Contains no own state — all logic is stateless.
 */
interface UsageQueryService {
    suspend fun getByTurn(turnId: TurnId): Either<DomainError, UsageRecord>
    suspend fun getBySession(sessionId: AgentSessionId): Either<DomainError, Map<TurnId, UsageRecord>>
    suspend fun getSessionTotal(sessionId: AgentSessionId): Either<DomainError, UsageRecord>
}
