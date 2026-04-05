package com.ai.challenge.core

sealed interface CompressionDecision {
    data object Skip : CompressionDecision
    data class Compress(val partitionPoint: Int) : CompressionDecision
}

interface CompressionStrategy {
    fun evaluate(context: CompressionContext): CompressionDecision
}
