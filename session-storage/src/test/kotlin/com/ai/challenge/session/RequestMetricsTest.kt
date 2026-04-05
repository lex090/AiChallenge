package com.ai.challenge.session

import kotlin.test.Test
import kotlin.test.assertEquals

class TurnIdTest {
    @Test
    fun `generate creates unique TurnIds`() {
        val id1 = TurnId.generate()
        val id2 = TurnId.generate()
        assert(id1 != id2)
    }
}

class TokenDetailsTest {
    @Test
    fun `totalTokens is sum of prompt and completion`() {
        val details = TokenDetails(promptTokens = 100, completionTokens = 50)
        assertEquals(150, details.totalTokens)
    }

    @Test
    fun `plus accumulates all fields`() {
        val a = TokenDetails(promptTokens = 10, completionTokens = 5, cachedTokens = 3, cacheWriteTokens = 2, reasoningTokens = 1)
        val b = TokenDetails(promptTokens = 20, completionTokens = 10, cachedTokens = 6, cacheWriteTokens = 4, reasoningTokens = 2)
        val sum = a + b
        assertEquals(TokenDetails(promptTokens = 30, completionTokens = 15, cachedTokens = 9, cacheWriteTokens = 6, reasoningTokens = 3), sum)
    }

    @Test
    fun `default values are all zero`() {
        val details = TokenDetails()
        assertEquals(0, details.promptTokens)
        assertEquals(0, details.completionTokens)
        assertEquals(0, details.cachedTokens)
        assertEquals(0, details.cacheWriteTokens)
        assertEquals(0, details.reasoningTokens)
        assertEquals(0, details.totalTokens)
    }
}

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

class RequestMetricsTest {
    @Test
    fun `plus accumulates tokens and cost`() {
        val a = RequestMetrics(
            tokens = TokenDetails(promptTokens = 10, completionTokens = 5),
            cost = CostDetails(totalCost = 0.001),
        )
        val b = RequestMetrics(
            tokens = TokenDetails(promptTokens = 20, completionTokens = 10),
            cost = CostDetails(totalCost = 0.002),
        )
        val sum = a + b
        assertEquals(30, sum.tokens.promptTokens)
        assertEquals(15, sum.tokens.completionTokens)
        assertEquals(0.003, sum.cost.totalCost, 1e-9)
    }

    @Test
    fun `default is empty tokens and cost`() {
        val metrics = RequestMetrics()
        assertEquals(TokenDetails(), metrics.tokens)
        assertEquals(CostDetails(), metrics.cost)
    }
}
