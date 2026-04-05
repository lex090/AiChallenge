package com.ai.challenge.context

import com.ai.challenge.core.context.CompressionContext
import com.ai.challenge.core.context.CompressionDecision
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.turn.Turn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TurnCountStrategyTest {

    private fun turns(count: Int): List<Turn> =
        (1..count).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") }

    private fun context(turnCount: Int, lastSummary: Summary? = null) =
        CompressionContext(history = turns(turnCount), lastSummary = lastSummary)

    private fun summaryAt(toTurnIndex: Int) =
        Summary(text = "summary", fromTurnIndex = 0, toTurnIndex = toTurnIndex)

    // --- Skip cases ---

    @Test
    fun `returns Skip when no prior compression and history below maxTurns`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 3)
        assertIs<CompressionDecision.Skip>(strategy.evaluate(context(3)))
    }

    @Test
    fun `returns Skip when no prior compression and empty history`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 3)
        assertIs<CompressionDecision.Skip>(strategy.evaluate(context(0)))
    }

    @Test
    fun `returns Skip when not enough turns accumulated after compression`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 5)
        // turnsSinceCompression = 8 - 3 = 5, threshold = 2 + 5 = 7, 5 < 7
        assertIs<CompressionDecision.Skip>(strategy.evaluate(context(8, summaryAt(3))))
    }

    // --- Compress cases ---

    @Test
    fun `returns Compress with correct partitionPoint at maxTurns`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 3)
        val decision = strategy.evaluate(context(5))
        assertIs<CompressionDecision.Compress>(decision)
        assertEquals(3, decision.partitionPoint)
    }

    @Test
    fun `returns Compress when history exceeds maxTurns`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 3)
        val decision = strategy.evaluate(context(6))
        assertIs<CompressionDecision.Compress>(decision)
        assertEquals(4, decision.partitionPoint)
    }

    @Test
    fun `returns Compress when enough turns after prior compression`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 5)
        // turnsSinceCompression = 11 - 3 = 8, threshold = 2 + 5 = 7, 8 >= 7
        val decision = strategy.evaluate(context(11, summaryAt(3)))
        assertIs<CompressionDecision.Compress>(decision)
        assertEquals(9, decision.partitionPoint)
    }

    @Test
    fun `returns Compress when exactly at threshold after compression`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 5)
        // turnsSinceCompression = 10 - 3 = 7, threshold = 2 + 5 = 7, 7 >= 7
        val decision = strategy.evaluate(context(10, summaryAt(3)))
        assertIs<CompressionDecision.Compress>(decision)
        assertEquals(8, decision.partitionPoint)
    }

    @Test
    fun `partitionPoint is 0 when retainLast exceeds history size`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 20, compressionInterval = 3)
        val decision = strategy.evaluate(context(6))
        assertIs<CompressionDecision.Compress>(decision)
        assertEquals(0, decision.partitionPoint)
    }
}
