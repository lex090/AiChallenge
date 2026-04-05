package com.ai.challenge.core.metrics

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId

interface CostRepository {
    suspend fun record(sessionId: AgentSessionId, turnId: TurnId, details: CostDetails)
    suspend fun getByTurn(turnId: TurnId): CostDetails?
    suspend fun getBySession(sessionId: AgentSessionId): Map<TurnId, CostDetails>
    suspend fun getSessionTotal(sessionId: AgentSessionId): CostDetails
}
