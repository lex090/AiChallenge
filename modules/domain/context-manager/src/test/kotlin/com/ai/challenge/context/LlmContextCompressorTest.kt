package com.ai.challenge.context

import arrow.core.Either
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.llm.LlmPort
import com.ai.challenge.core.llm.LlmResponse
import com.ai.challenge.core.llm.ResponseFormat
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.usage.model.UsageRecord
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LlmContextCompressorTest {

    @Test
    fun `compress sends conversation to LLM and returns summary text`() = runTest {
        val fakeLlm = FakeCompressorLlmPort(response = Either.Right(
            value = LlmResponse(
                content = MessageContent(value = "This is a summary."),
                usage = UsageRecord.ZERO,
            )
        ))
        val compressor = LlmContextCompressor(llmPort = fakeLlm)

        val sessionId = AgentSessionId(value = "test-session")
        val turns = listOf(
            createTestTurn(sessionId = sessionId, userMessage = "Hello", assistantMessage = "Hi there!"),
            createTestTurn(sessionId = sessionId, userMessage = "How are you?", assistantMessage = "I'm fine!"),
        )

        val result = compressor.compress(turns = turns, previousSummary = null)

        assertEquals("This is a summary.", result.value)

        val messages = fakeLlm.lastMessages!!

        // system + 2 user + 2 assistant + final user instruction = 6
        assertEquals(6, messages.size)
        assertEquals(MessageRole.System, messages[0].role)
        assertEquals(MessageRole.User, messages[1].role)
        assertEquals(MessageContent(value = "Hello"), messages[1].content)
        assertEquals(MessageRole.Assistant, messages[2].role)
        assertEquals(MessageContent(value = "Hi there!"), messages[2].content)
        assertEquals(MessageRole.User, messages[3].role)
        assertEquals(MessageContent(value = "How are you?"), messages[3].content)
        assertEquals(MessageRole.Assistant, messages[4].role)
        assertEquals(MessageContent(value = "I'm fine!"), messages[4].content)
        // Last message is the summarization instruction
        assertEquals(MessageRole.User, messages[5].role)
        assertTrue(messages[5].content.value.contains("summary"))
    }

    @Test
    fun `compress returns fallback when LLM returns error`() = runTest {
        val fakeLlm = FakeCompressorLlmPort(response = Either.Left(
            value = DomainError.NetworkError(message = "Connection refused"),
        ))
        val compressor = LlmContextCompressor(llmPort = fakeLlm)

        val sessionId = AgentSessionId(value = "test-session")
        val turns = listOf(
            createTestTurn(sessionId = sessionId, userMessage = "Hello", assistantMessage = "Hi!"),
        )

        val result = compressor.compress(turns = turns, previousSummary = null)

        assertEquals("Summary unavailable", result.value)
    }
}

private class FakeCompressorLlmPort(
    private val response: Either<DomainError, LlmResponse>,
) : LlmPort {
    var lastMessages: List<ContextMessage>? = null
    var lastResponseFormat: ResponseFormat? = null

    override suspend fun complete(
        messages: List<ContextMessage>,
        responseFormat: ResponseFormat,
    ): Either<DomainError, LlmResponse> {
        lastMessages = messages
        lastResponseFormat = responseFormat
        return response
    }
}
