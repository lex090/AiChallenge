package com.ai.challenge.agent

import arrow.core.Either

interface Agent {
    suspend fun send(message: String): Either<AgentError, String>
}
