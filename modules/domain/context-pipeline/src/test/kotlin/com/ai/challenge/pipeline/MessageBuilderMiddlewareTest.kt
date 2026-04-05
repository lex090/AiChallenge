package com.ai.challenge.pipeline

import com.ai.challenge.core.ContextState
import com.ai.challenge.core.Fact
import com.ai.challenge.core.MessageRole
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageBuilderMiddlewareTest {

    private val sessionId = SessionId.generate()

    @Test
    fun `builds messages from history and new message`() = runTest {
        val middleware = MessageBuilderMiddleware()
        val history = listOf(
            Turn(userMessage = "hello", agentResponse = "hi"),
            Turn(userMessage = "how are you", agentResponse = "good"),
        )
        val state = ContextState(sessionId, history, "bye")
        val result = middleware.process(state) { it }

        assertEquals(5, result.messages.size)
        assertEquals(MessageRole.User, result.messages[0].role)
        assertEquals("hello", result.messages[0].content)
        assertEquals(MessageRole.Assistant, result.messages[1].role)
        assertEquals("hi", result.messages[1].content)
        assertEquals(MessageRole.User, result.messages[2].role)
        assertEquals("how are you", result.messages[2].content)
        assertEquals(MessageRole.Assistant, result.messages[3].role)
        assertEquals("good", result.messages[3].content)
        assertEquals(MessageRole.User, result.messages[4].role)
        assertEquals("bye", result.messages[4].content)
    }

    @Test
    fun `prepends facts as system message when facts present`() = runTest {
        val middleware = MessageBuilderMiddleware()
        val facts = listOf(Fact(content = "User prefers Kotlin"), Fact(content = "Project uses Gradle"))
        val history = listOf(Turn(userMessage = "hello", agentResponse = "hi"))
        val state = ContextState(sessionId, history, "bye", facts = facts)
        val result = middleware.process(state) { it }

        assertEquals(4, result.messages.size)
        assertEquals(MessageRole.System, result.messages[0].role)
        assert(result.messages[0].content.contains("User prefers Kotlin"))
        assert(result.messages[0].content.contains("Project uses Gradle"))
        assertEquals(MessageRole.User, result.messages[1].role)
    }

    @Test
    fun `no system message when no facts`() = runTest {
        val middleware = MessageBuilderMiddleware()
        val state = ContextState(sessionId, emptyList(), "hello")
        val result = middleware.process(state) { it }

        assertEquals(1, result.messages.size)
        assertEquals(MessageRole.User, result.messages[0].role)
        assertEquals("hello", result.messages[0].content)
    }
}
