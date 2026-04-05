package com.ai.challenge.core

import com.ai.challenge.core.cost.CostDetails
import kotlin.test.Test
import kotlin.test.assertEquals

class CostDetailsTest {
    @Test
    fun `plus accumulates all fields`() {
        val a = CostDetails(totalCost = 0.001, upstreamCost = 0.0008, upstreamPromptCost = 0.0005, upstreamCompletionsCost = 0.0003)
        val b = CostDetails(totalCost = 0.002, upstreamCost = 0.0016, upstreamPromptCost = 0.001, upstreamCompletionsCost = 0.0006)
        val sum = a + b
        assertEquals(0.003, sum.totalCost, 1e-9)
        assertEquals(0.0024, sum.upstreamCost, 1e-9)
        assertEquals(0.0015, sum.upstreamPromptCost, 1e-9)
        assertEquals(0.0009, sum.upstreamCompletionsCost, 1e-9)
    }

    @Test
    fun `default values are all zero`() {
        val cost = CostDetails()
        assertEquals(0.0, cost.totalCost)
        assertEquals(0.0, cost.upstreamCost)
        assertEquals(0.0, cost.upstreamPromptCost)
        assertEquals(0.0, cost.upstreamCompletionsCost)
    }
}
