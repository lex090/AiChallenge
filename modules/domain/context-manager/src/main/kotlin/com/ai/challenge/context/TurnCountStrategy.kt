package com.ai.challenge.context

import com.ai.challenge.core.CompressionContext
import com.ai.challenge.core.CompressionStrategy

class TurnCountStrategy(
    private val maxTurns: Int,
    private val retainLast: Int,
    private val compressionInterval: Int = maxTurns - retainLast,
) : CompressionStrategy {

    override fun shouldCompress(context: CompressionContext): Boolean {
        val lastCompressedIndex = context.lastSummary?.toTurnIndex
        if (lastCompressedIndex == null) {
            return context.history.size >= maxTurns
        }
        val turnsSinceCompression = context.history.size - lastCompressedIndex
        return turnsSinceCompression >= retainLast + compressionInterval
    }

    override fun partitionPoint(context: CompressionContext): Int =
        (context.history.size - retainLast).coerceAtLeast(0)
}
