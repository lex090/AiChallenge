package com.ai.challenge.core.session

import kotlin.time.Clock
import kotlin.time.Instant

data class AgentSession(
    val id: AgentSessionId,
    val title: String = "",
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
)
