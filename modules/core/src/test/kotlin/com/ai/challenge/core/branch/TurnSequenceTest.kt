package com.ai.challenge.core.branch

import com.ai.challenge.core.turn.TurnId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TurnSequenceTest {

    @Test
    fun `trunkUpTo returns sequence up to and including the given turn`() {
        val t1 = TurnId(value = "t1")
        val t2 = TurnId(value = "t2")
        val t3 = TurnId(value = "t3")
        val seq = TurnSequence(values = listOf(t1, t2, t3))

        val trunk = seq.trunkUpTo(turnId = t2)

        assertEquals(expected = listOf(t1, t2), actual = trunk.values)
    }

    @Test
    fun `trunkUpTo with first turn returns single element`() {
        val t1 = TurnId(value = "t1")
        val t2 = TurnId(value = "t2")
        val seq = TurnSequence(values = listOf(t1, t2))

        val trunk = seq.trunkUpTo(turnId = t1)

        assertEquals(expected = listOf(t1), actual = trunk.values)
    }

    @Test
    fun `trunkUpTo with last turn returns full sequence`() {
        val t1 = TurnId(value = "t1")
        val t2 = TurnId(value = "t2")
        val seq = TurnSequence(values = listOf(t1, t2))

        val trunk = seq.trunkUpTo(turnId = t2)

        assertEquals(expected = listOf(t1, t2), actual = trunk.values)
    }

    @Test
    fun `trunkUpTo throws for unknown turn`() {
        val t1 = TurnId(value = "t1")
        val seq = TurnSequence(values = listOf(t1))

        assertFailsWith<IllegalArgumentException> {
            seq.trunkUpTo(turnId = TurnId(value = "unknown"))
        }
    }
}
