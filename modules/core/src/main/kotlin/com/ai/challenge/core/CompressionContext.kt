package com.ai.challenge.core

data class CompressionContext(
    val history: List<Turn>,
    val lastSummary: Summary?,
)
