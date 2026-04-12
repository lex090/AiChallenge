package com.ai.challenge.conversation.service

import arrow.core.Either
import com.ai.challenge.conversation.model.UsageRecord
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.TurnId

/**
 * Domain Service -- read-only usage metrics aggregation.
 *
 * Queries [Turn] data and aggregates [UsageRecord]
 * by turn, session, or session total.
 *
 * Read-only service -- does not mutate any data.
 * Contains no own state -- all logic is stateless.
 */
interface UsageQueryService {
    suspend fun getByTurn(turnId: TurnId): Either<DomainError, UsageRecord>
    suspend fun getBySession(sessionId: AgentSessionId): Either<DomainError, Map<TurnId, UsageRecord>>
    suspend fun getSessionTotal(sessionId: AgentSessionId): Either<DomainError, UsageRecord>
}
