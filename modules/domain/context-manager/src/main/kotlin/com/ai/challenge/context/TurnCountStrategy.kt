package com.ai.challenge.context

import com.ai.challenge.core.CompressionContext
import com.ai.challenge.core.CompressionDecision
import com.ai.challenge.core.CompressionStrategy

class TurnCountStrategy(
    private val maxTurns: Int,
    private val retainLast: Int,
    private val compressionInterval: Int = maxTurns - retainLast,
) : CompressionStrategy {

    override fun evaluate(context: CompressionContext): CompressionDecision {
        val shouldCompress = when (val lastIndex = context.lastSummary?.toTurnIndex) {
            null -> context.history.size >= maxTurns
            else -> context.history.size - lastIndex >= retainLast + compressionInterval
        }

        if (!shouldCompress) return CompressionDecision.Skip

        val partitionPoint = (context.history.size - retainLast).coerceAtLeast(0)
        return CompressionDecision.Compress(partitionPoint)
    }
}
