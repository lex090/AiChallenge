package com.ai.challenge.core.agent

import arrow.core.Either
import com.ai.challenge.core.session.AgentSessionId

interface ChatAgent {
    suspend fun send(sessionId: AgentSessionId, message: String): Either<AgentError, AgentResponse>
}
