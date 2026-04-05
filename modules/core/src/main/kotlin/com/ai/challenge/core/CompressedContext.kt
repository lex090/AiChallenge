package com.ai.challenge.core

data class CompressedContext(
    val messages: List<ContextMessage>,
    val compressed: Boolean,
    val originalTurnCount: Int,
    val retainedTurnCount: Int,
    val summaryCount: Int,
)
