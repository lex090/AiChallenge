package com.ai.challenge.context

import com.ai.challenge.core.CompressionContext
import com.ai.challenge.core.Summary
import com.ai.challenge.core.Turn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TurnCountStrategyTest {

    private fun turns(count: Int): List<Turn> =
        (1..count).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") }

    private fun context(turnCount: Int, lastSummary: Summary? = null) =
        CompressionContext(history = turns(turnCount), lastSummary = lastSummary)

    private fun summaryAt(toTurnIndex: Int) =
        Summary(text = "summary", fromTurnIndex = 0, toTurnIndex = toTurnIndex)

    // --- shouldCompress without previous compression ---

    @Test
    fun `shouldCompress returns true when no prior compression and history at maxTurns`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 3)
        assertTrue(strategy.shouldCompress(context(5)))
    }

    @Test
    fun `shouldCompress returns false when no prior compression and history below maxTurns`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 3)
        assertFalse(strategy.shouldCompress(context(3)))
    }

    @Test
    fun `shouldCompress returns true when no prior compression and history exceeds maxTurns`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 3)
        assertTrue(strategy.shouldCompress(context(6)))
    }

    @Test
    fun `shouldCompress returns false when no prior compression and empty history`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 3)
        assertFalse(strategy.shouldCompress(context(0)))
    }

    // --- shouldCompress with previous compression ---

    @Test
    fun `shouldCompress returns false when not enough turns accumulated after compression`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 5)
        // turnsSinceCompression = 8 - 3 = 5, threshold = 2 + 5 = 7, 5 < 7
        assertFalse(strategy.shouldCompress(context(8, summaryAt(3))))
    }

    @Test
    fun `shouldCompress returns true when enough turns accumulated after compression`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 5)
        // turnsSinceCompression = 11 - 3 = 8, threshold = 2 + 5 = 7, 8 >= 7
        assertTrue(strategy.shouldCompress(context(11, summaryAt(3))))
    }

    @Test
    fun `shouldCompress returns true when exactly at threshold after compression`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 5)
        // turnsSinceCompression = 10 - 3 = 7, threshold = 2 + 5 = 7, 7 >= 7
        assertTrue(strategy.shouldCompress(context(10, summaryAt(3))))
    }

    // --- partitionPoint ---

    @Test
    fun `partitionPoint returns correct split index`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 3, compressionInterval = 3)
        assertEquals(7, strategy.partitionPoint(context(10)))
    }

    @Test
    fun `partitionPoint returns 0 when retainLast exceeds history size`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 20, compressionInterval = 3)
        assertEquals(0, strategy.partitionPoint(context(10)))
    }

    @Test
    fun `partitionPoint returns 0 for empty history`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 3, compressionInterval = 3)
        assertEquals(0, strategy.partitionPoint(context(0)))
    }
}
