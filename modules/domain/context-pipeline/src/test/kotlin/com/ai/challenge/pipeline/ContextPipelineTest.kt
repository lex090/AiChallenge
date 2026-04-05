package com.ai.challenge.pipeline

import com.ai.challenge.core.ContextMiddleware
import com.ai.challenge.core.ContextPipeline
import com.ai.challenge.core.ContextState
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ContextPipelineTest {

    private val sessionId = SessionId.generate()

    @Test
    fun `empty pipeline returns state unchanged`() = runTest {
        val pipeline = ContextPipeline(emptyList())
        val state = ContextState(sessionId, emptyList(), "hello")
        val result = pipeline.execute(state)
        assertEquals(state, result)
    }

    @Test
    fun `single middleware is applied`() = runTest {
        val middleware = ContextMiddleware { state, next ->
            next(state.copy(newMessage = state.newMessage.uppercase()))
        }
        val pipeline = ContextPipeline(listOf(middleware))
        val state = ContextState(sessionId, emptyList(), "hello")
        val result = pipeline.execute(state)
        assertEquals("HELLO", result.newMessage)
    }

    @Test
    fun `middlewares execute in order`() = runTest {
        val order = mutableListOf<String>()
        val first = ContextMiddleware { state, next ->
            order.add("first-before")
            val result = next(state)
            order.add("first-after")
            result
        }
        val second = ContextMiddleware { state, next ->
            order.add("second-before")
            val result = next(state)
            order.add("second-after")
            result
        }
        val pipeline = ContextPipeline(listOf(first, second))
        pipeline.execute(ContextState(sessionId, emptyList(), "test"))
        assertEquals(listOf("first-before", "second-before", "second-after", "first-after"), order)
    }

    @Test
    fun `middleware can short-circuit by not calling next`() = runTest {
        val shortCircuit = ContextMiddleware { state, _ ->
            state.copy(newMessage = "intercepted")
        }
        val neverCalled = ContextMiddleware { _, _ ->
            error("should not be called")
        }
        val pipeline = ContextPipeline(listOf(shortCircuit, neverCalled))
        val result = pipeline.execute(ContextState(sessionId, emptyList(), "test"))
        assertEquals("intercepted", result.newMessage)
    }
}
