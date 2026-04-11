package com.ai.challenge.core.token

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.model.TokenCount

interface TokenDetailsRepository {
    suspend fun record(turnId: TurnId, promptTokens: TokenCount, completionTokens: TokenCount)
    suspend fun getByTurn(turnId: TurnId): TokenCount?
    suspend fun getBySession(sessionId: AgentSessionId): Map<TurnId, TokenCount>
    suspend fun getSessionTotal(sessionId: AgentSessionId): TokenCount
}
