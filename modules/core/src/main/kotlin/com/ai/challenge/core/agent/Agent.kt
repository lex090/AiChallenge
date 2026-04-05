package com.ai.challenge.core.agent

import arrow.core.Either
import com.ai.challenge.core.metrics.CostDetails
import com.ai.challenge.core.metrics.TokenDetails
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.SessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId

interface Agent {
    suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse>
    suspend fun createSession(title: String = ""): SessionId
    suspend fun deleteSession(id: SessionId): Boolean
    suspend fun listSessions(): List<AgentSession>
    suspend fun getSession(id: SessionId): AgentSession?
    suspend fun updateSessionTitle(id: SessionId, title: String)
    suspend fun getTurns(sessionId: SessionId, limit: Int? = null): List<Turn>
    suspend fun getTokensByTurn(turnId: TurnId): TokenDetails?
    suspend fun getTokensBySession(sessionId: SessionId): Map<TurnId, TokenDetails>
    suspend fun getSessionTotalTokens(sessionId: SessionId): TokenDetails
    suspend fun getCostByTurn(turnId: TurnId): CostDetails?
    suspend fun getCostBySession(sessionId: SessionId): Map<TurnId, CostDetails>
    suspend fun getSessionTotalCost(sessionId: SessionId): CostDetails
}
