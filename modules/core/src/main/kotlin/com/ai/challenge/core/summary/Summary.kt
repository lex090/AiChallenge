package com.ai.challenge.core.summary

import kotlin.time.Instant

data class Summary(
    val id: SummaryId,
    val text: String,
    val fromTurnIndex: Int,
    val toTurnIndex: Int,
    val createdAt: Instant,
)
