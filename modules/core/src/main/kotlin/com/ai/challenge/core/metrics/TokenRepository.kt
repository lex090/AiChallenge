package com.ai.challenge.core.metrics

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId

interface TokenRepository {
    suspend fun record(sessionId: AgentSessionId, turnId: TurnId, details: TokenDetails)
    suspend fun getByTurn(turnId: TurnId): TokenDetails?
    suspend fun getBySession(sessionId: AgentSessionId): Map<TurnId, TokenDetails>
    suspend fun getSessionTotal(sessionId: AgentSessionId): TokenDetails
}
