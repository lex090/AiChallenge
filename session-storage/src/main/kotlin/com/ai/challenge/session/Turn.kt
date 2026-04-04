package com.ai.challenge.session

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class Turn(
    val userMessage: String,
    val agentResponse: String,
    val timestamp: Instant = Clock.System.now(),
)
