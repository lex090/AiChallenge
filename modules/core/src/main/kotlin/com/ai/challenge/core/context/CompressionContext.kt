package com.ai.challenge.core.context

import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.turn.Turn

data class CompressionContext(
    val history: List<Turn>,
    val lastSummary: Summary?,
)
