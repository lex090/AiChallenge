package com.ai.challenge.core

import kotlin.time.Clock
import kotlin.time.Instant

data class Summary(
    val id: SummaryId = SummaryId.generate(),
    val text: String,
    val fromTurnIndex: Int,
    val toTurnIndex: Int,
    val createdAt: Instant = Clock.System.now(),
)
