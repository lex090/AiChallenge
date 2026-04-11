package com.ai.challenge.core.usage

import arrow.core.Either
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.model.UsageRecord

interface UsageService {
    suspend fun getByTurn(turnId: TurnId): Either<DomainError, UsageRecord>
    suspend fun getBySession(sessionId: AgentSessionId): Either<DomainError, Map<TurnId, UsageRecord>>
    suspend fun getSessionTotal(sessionId: AgentSessionId): Either<DomainError, UsageRecord>
}
