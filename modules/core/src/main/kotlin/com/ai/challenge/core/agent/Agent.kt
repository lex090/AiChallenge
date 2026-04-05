package com.ai.challenge.core.agent

import arrow.core.Either
import com.ai.challenge.core.metrics.CostDetails
import com.ai.challenge.core.metrics.TokenDetails
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId

interface Agent {
    suspend fun send(sessionId: AgentSessionId, message: String): Either<AgentError, AgentResponse>
    suspend fun createSession(title: String = ""): AgentSessionId
    suspend fun deleteSession(id: AgentSessionId): Boolean
    suspend fun listSessions(): List<AgentSession>
    suspend fun getSession(id: AgentSessionId): AgentSession?
    suspend fun updateSessionTitle(id: AgentSessionId, title: String)
    suspend fun getTurns(sessionId: AgentSessionId, limit: Int? = null): List<Turn>
    suspend fun getTokensByTurn(turnId: TurnId): TokenDetails?
    suspend fun getTokensBySession(sessionId: AgentSessionId): Map<TurnId, TokenDetails>
    suspend fun getSessionTotalTokens(sessionId: AgentSessionId): TokenDetails
    suspend fun getCostByTurn(turnId: TurnId): CostDetails?
    suspend fun getCostBySession(sessionId: AgentSessionId): Map<TurnId, CostDetails>
    suspend fun getSessionTotalCost(sessionId: AgentSessionId): CostDetails
}
