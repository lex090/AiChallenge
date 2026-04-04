package com.ai.challenge.agent

import arrow.core.Either
import com.ai.challenge.session.SessionId

interface Agent {
    suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse>
}
