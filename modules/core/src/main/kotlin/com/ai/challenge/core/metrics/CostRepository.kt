package com.ai.challenge.core.metrics

import com.ai.challenge.core.session.SessionId
import com.ai.challenge.core.turn.TurnId

interface CostRepository {
    suspend fun record(sessionId: SessionId, turnId: TurnId, details: CostDetails)
    suspend fun getByTurn(turnId: TurnId): CostDetails?
    suspend fun getBySession(sessionId: SessionId): Map<TurnId, CostDetails>
    suspend fun getSessionTotal(sessionId: SessionId): CostDetails
}
