package com.ai.challenge.context

import com.ai.challenge.core.context.CompressionContext
import com.ai.challenge.core.context.CompressionDecision
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.turn.Turn
import kotlin.test.Test
import kotlin.test.assertIs

class ContextStrategyFactoryTest {

    private val factory = ContextStrategyFactory()

    @Test
    fun `None type creates strategy that always skips`() {
        val strategy = factory.create(ContextManagementType.None)
        val context = CompressionContext(
            history = (1..100).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") },
            lastSummary = null,
        )
        assertIs<CompressionDecision.Skip>(strategy.evaluate(context))
    }

    @Test
    fun `SummarizeOnThreshold type creates strategy that compresses at threshold`() {
        val strategy = factory.create(ContextManagementType.SummarizeOnThreshold)
        val context = CompressionContext(
            history = (1..20).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") },
            lastSummary = null,
        )
        assertIs<CompressionDecision.Compress>(strategy.evaluate(context))
    }

    @Test
    fun `SummarizeOnThreshold type skips when below threshold`() {
        val strategy = factory.create(ContextManagementType.SummarizeOnThreshold)
        val context = CompressionContext(
            history = (1..3).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") },
            lastSummary = null,
        )
        assertIs<CompressionDecision.Skip>(strategy.evaluate(context))
    }
}
