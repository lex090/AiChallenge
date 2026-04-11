package com.ai.challenge.core.turn

import com.ai.challenge.core.session.AgentSessionId
import kotlin.time.Instant

data class Turn(
    val id: TurnId,
    val sessionId: AgentSessionId,
    val userMessage: String,
    val agentResponse: String,
    val timestamp: Instant,
)
