package com.ai.challenge.pipeline

import com.ai.challenge.core.ContextPipeline
import com.ai.challenge.core.ContextStrategyType
import com.ai.challenge.core.MessageRole
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PipelineContextManagerTest {

    private val sessionId = SessionId.generate()

    @Test
    fun `sliding window pipeline trims and builds messages`() = runTest {
        val manager = PipelineContextManager(
            pipelines = mapOf(
                ContextStrategyType.SlidingWindow to ContextPipelines.slidingWindow(windowSize = 2),
            )
        )
        manager.activeStrategy = ContextStrategyType.SlidingWindow

        val history = (1..5).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") }
        val result = manager.prepareContext(sessionId, history, "new message")

        // window=2 => 2 turns retained => 4 turn messages + 1 new user message = 5
        assertEquals(5, result.messages.size)
        assertEquals(5, result.originalTurnCount)
        assertEquals(2, result.retainedTurnCount)
        assertEquals(MessageRole.User, result.messages[0].role)
        assertEquals("msg4", result.messages[0].content)
        assertEquals(MessageRole.User, result.messages.last().role)
        assertEquals("new message", result.messages.last().content)
    }

    @Test
    fun `switching strategy changes pipeline`() = runTest {
        val smallWindow = ContextPipelines.slidingWindow(windowSize = 2)
        val largeWindow = ContextPipelines.slidingWindow(windowSize = 100)
        val manager = PipelineContextManager(
            pipelines = mapOf(
                ContextStrategyType.SlidingWindow to smallWindow,
                ContextStrategyType.StickyFacts to largeWindow,
            )
        )
        val history = (1..5).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") }

        manager.activeStrategy = ContextStrategyType.SlidingWindow
        val result1 = manager.prepareContext(sessionId, history, "test")
        assertEquals(2, result1.retainedTurnCount)

        manager.activeStrategy = ContextStrategyType.StickyFacts
        val result2 = manager.prepareContext(sessionId, history, "test")
        assertEquals(5, result2.retainedTurnCount)
    }
}
