package com.ai.challenge.core.agent

import arrow.core.Either
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.model.UsageRecord

interface UsageTracker {
    suspend fun getUsageByTurn(turnId: TurnId): Either<AgentError, UsageRecord>
    suspend fun getUsageBySession(sessionId: AgentSessionId): Either<AgentError, Map<TurnId, UsageRecord>>
    suspend fun getSessionTotalUsage(sessionId: AgentSessionId): Either<AgentError, UsageRecord>
}
