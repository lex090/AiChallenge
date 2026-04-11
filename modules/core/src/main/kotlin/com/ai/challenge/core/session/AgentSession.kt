package com.ai.challenge.core.session

import kotlin.time.Instant

data class AgentSession(
    val id: AgentSessionId,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
