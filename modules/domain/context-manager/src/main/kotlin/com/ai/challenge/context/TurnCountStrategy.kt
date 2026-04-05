package com.ai.challenge.context

import com.ai.challenge.core.CompressionStrategy
import com.ai.challenge.core.Turn

class TurnCountStrategy(
    private val maxTurns: Int,
    private val retainLast: Int,
) : CompressionStrategy {

    override fun shouldCompress(history: List<Turn>): Boolean =
        history.size > maxTurns

    override fun partitionPoint(history: List<Turn>): Int =
        (history.size - retainLast).coerceAtLeast(0)
}
