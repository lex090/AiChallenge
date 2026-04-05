package com.ai.challenge.core.metrics

import com.ai.challenge.core.session.SessionId
import com.ai.challenge.core.turn.TurnId

interface TokenRepository {
    suspend fun record(sessionId: SessionId, turnId: TurnId, details: TokenDetails)
    suspend fun getByTurn(turnId: TurnId): TokenDetails?
    suspend fun getBySession(sessionId: SessionId): Map<TurnId, TokenDetails>
    suspend fun getSessionTotal(sessionId: SessionId): TokenDetails
}
