package com.ai.challenge.core.context

sealed interface CompressionDecision {
    data object Skip : CompressionDecision
    data class Compress(val partitionPoint: Int) : CompressionDecision
}

interface CompressionStrategy {
    fun evaluate(context: CompressionContext): CompressionDecision
}
