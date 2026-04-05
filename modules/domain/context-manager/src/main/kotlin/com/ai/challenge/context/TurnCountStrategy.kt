package com.ai.challenge.context

import com.ai.challenge.core.CompressionStrategy
import com.ai.challenge.core.Turn

class TurnCountStrategy(
    private val maxTurns: Int,
    private val retainLast: Int,
    private val compressionInterval: Int = maxTurns - retainLast,
) : CompressionStrategy {

    override fun shouldCompress(history: List<Turn>, lastCompressedIndex: Int?): Boolean {
        if (lastCompressedIndex == null) {
            return history.size > maxTurns
        }
        val turnsSinceCompression = history.size - lastCompressedIndex
        return turnsSinceCompression > retainLast + compressionInterval
    }

    override fun partitionPoint(history: List<Turn>): Int =
        (history.size - retainLast).coerceAtLeast(0)
}
