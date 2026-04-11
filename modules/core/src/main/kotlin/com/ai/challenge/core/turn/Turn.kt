package com.ai.challenge.core.turn

import kotlin.time.Instant

data class Turn(
    val id: TurnId,
    val userMessage: String,
    val agentResponse: String,
    val timestamp: Instant,
)
