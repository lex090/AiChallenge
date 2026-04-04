package com.ai.challenge.session

import kotlin.time.Clock
import kotlin.time.Instant

data class Turn(
    val id: TurnId = TurnId.generate(),
    val userMessage: String,
    val agentResponse: String,
    val timestamp: Instant = Clock.System.now(),
)
