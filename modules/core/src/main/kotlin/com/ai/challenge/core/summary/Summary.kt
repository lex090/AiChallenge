package com.ai.challenge.core.summary

import com.ai.challenge.core.session.AgentSessionId
import kotlin.time.Instant

data class Summary(
    val id: SummaryId,
    val sessionId: AgentSessionId,
    val text: String,
    val fromTurnIndex: Int,
    val toTurnIndex: Int,
    val createdAt: Instant,
)
