package com.ai.challenge.context

import com.ai.challenge.core.Turn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TurnCountStrategyTest {

    private fun turns(count: Int): List<Turn> =
        (1..count).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") }

    @Test
    fun `shouldCompress returns false when history size equals maxTurns`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2)
        assertFalse(strategy.shouldCompress(turns(5)))
    }

    @Test
    fun `shouldCompress returns false when history is smaller than maxTurns`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2)
        assertFalse(strategy.shouldCompress(turns(3)))
    }

    @Test
    fun `shouldCompress returns true when history exceeds maxTurns`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2)
        assertTrue(strategy.shouldCompress(turns(6)))
    }

    @Test
    fun `shouldCompress returns false for empty history`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2)
        assertFalse(strategy.shouldCompress(emptyList()))
    }

    @Test
    fun `partitionPoint returns correct split index`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 3)
        assertEquals(7, strategy.partitionPoint(turns(10)))
    }

    @Test
    fun `partitionPoint returns 0 when retainLast exceeds history size`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 20)
        assertEquals(0, strategy.partitionPoint(turns(10)))
    }

    @Test
    fun `partitionPoint returns 0 for empty history`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 3)
        assertEquals(0, strategy.partitionPoint(emptyList()))
    }
}
