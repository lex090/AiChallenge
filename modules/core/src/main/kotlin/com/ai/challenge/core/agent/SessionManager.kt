package com.ai.challenge.core.agent

import arrow.core.Either
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId

interface SessionManager {
    suspend fun createSession(title: String): Either<AgentError, AgentSessionId>
    suspend fun deleteSession(id: AgentSessionId): Either<AgentError, Unit>
    suspend fun listSessions(): Either<AgentError, List<AgentSession>>
    suspend fun getSession(id: AgentSessionId): Either<AgentError, AgentSession>
    suspend fun updateSessionTitle(id: AgentSessionId, title: String): Either<AgentError, Unit>
    suspend fun getTurns(sessionId: AgentSessionId, limit: Int?): Either<AgentError, List<Turn>>
    suspend fun getContextManagementType(sessionId: AgentSessionId): Either<AgentError, ContextManagementType>
    suspend fun updateContextManagementType(sessionId: AgentSessionId, type: ContextManagementType): Either<AgentError, Unit>
}
