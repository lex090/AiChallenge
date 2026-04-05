package com.ai.challenge.pipeline

import com.ai.challenge.core.ContextState
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SlidingWindowMiddlewareTest {

    private val sessionId = SessionId.generate()

    private fun turns(count: Int) = (1..count).map {
        Turn(userMessage = "msg$it", agentResponse = "resp$it")
    }

    @Test
    fun `keeps all turns when under window size`() = runTest {
        val middleware = SlidingWindowMiddleware(windowSize = 10)
        val history = turns(5)
        val state = ContextState(sessionId, history, "new")
        val result = middleware.process(state) { it }
        assertEquals(5, result.history.size)
    }

    @Test
    fun `trims to window size when over`() = runTest {
        val middleware = SlidingWindowMiddleware(windowSize = 3)
        val history = turns(10)
        val state = ContextState(sessionId, history, "new")
        val result = middleware.process(state) { it }
        assertEquals(3, result.history.size)
        assertEquals("msg8", result.history[0].userMessage)
        assertEquals("msg10", result.history[2].userMessage)
    }

    @Test
    fun `exact window size keeps all`() = runTest {
        val middleware = SlidingWindowMiddleware(windowSize = 5)
        val history = turns(5)
        val state = ContextState(sessionId, history, "new")
        val result = middleware.process(state) { it }
        assertEquals(5, result.history.size)
    }
}
