package com.ai.challenge.core

interface CostRepository {
    suspend fun record(sessionId: SessionId, turnId: TurnId, details: CostDetails)
    suspend fun getByTurn(turnId: TurnId): CostDetails?
    suspend fun getBySession(sessionId: SessionId): Map<TurnId, CostDetails>
    suspend fun getSessionTotal(sessionId: SessionId): CostDetails
}
