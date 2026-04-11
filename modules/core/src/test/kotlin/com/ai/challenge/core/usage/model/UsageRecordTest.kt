package com.ai.challenge.core.usage.model

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageRecordTest {

    private fun record(
        promptTokens: Int,
        completionTokens: Int,
        cachedTokens: Int,
        cacheWriteTokens: Int,
        reasoningTokens: Int,
        totalCost: String,
        upstreamCost: String,
        upstreamPromptCost: String,
        upstreamCompletionsCost: String,
    ): UsageRecord = UsageRecord(
        promptTokens = TokenCount(value = promptTokens),
        completionTokens = TokenCount(value = completionTokens),
        cachedTokens = TokenCount(value = cachedTokens),
        cacheWriteTokens = TokenCount(value = cacheWriteTokens),
        reasoningTokens = TokenCount(value = reasoningTokens),
        totalCost = Cost(value = BigDecimal(totalCost)),
        upstreamCost = Cost(value = BigDecimal(upstreamCost)),
        upstreamPromptCost = Cost(value = BigDecimal(upstreamPromptCost)),
        upstreamCompletionsCost = Cost(value = BigDecimal(upstreamCompletionsCost)),
    )

    @Test
    fun `totalTokens sums prompt and completion`() {
        val usage = record(
            promptTokens = 100, completionTokens = 50, cachedTokens = 0,
            cacheWriteTokens = 0, reasoningTokens = 0,
            totalCost = "0", upstreamCost = "0", upstreamPromptCost = "0", upstreamCompletionsCost = "0",
        )
        assertEquals(TokenCount(value = 150), usage.totalTokens)
    }

    @Test
    fun `plus aggregates all fields`() {
        val a = record(
            promptTokens = 100, completionTokens = 50, cachedTokens = 20,
            cacheWriteTokens = 80, reasoningTokens = 10,
            totalCost = "0.0015", upstreamCost = "0.0012",
            upstreamPromptCost = "0.0008", upstreamCompletionsCost = "0.0004",
        )
        val b = record(
            promptTokens = 200, completionTokens = 100, cachedTokens = 30,
            cacheWriteTokens = 170, reasoningTokens = 20,
            totalCost = "0.003", upstreamCost = "0.0024",
            upstreamPromptCost = "0.0016", upstreamCompletionsCost = "0.0008",
        )
        val sum = a + b
        assertEquals(TokenCount(value = 300), sum.promptTokens)
        assertEquals(TokenCount(value = 150), sum.completionTokens)
        assertEquals(TokenCount(value = 50), sum.cachedTokens)
        assertEquals(TokenCount(value = 250), sum.cacheWriteTokens)
        assertEquals(TokenCount(value = 30), sum.reasoningTokens)
        assertEquals(Cost(value = BigDecimal("0.0045")), sum.totalCost)
        assertEquals(Cost(value = BigDecimal("0.0036")), sum.upstreamCost)
    }
}
