package com.ai.challenge.core

import com.ai.challenge.core.metrics.TokenDetails
import kotlin.test.Test
import kotlin.test.assertEquals

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
