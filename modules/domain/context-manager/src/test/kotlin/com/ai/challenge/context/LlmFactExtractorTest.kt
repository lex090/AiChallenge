package com.ai.challenge.context

import arrow.core.Either
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.llm.LlmPort
import com.ai.challenge.core.llm.LlmResponse
import com.ai.challenge.core.llm.ResponseFormat
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.usage.model.UsageRecord
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LlmFactExtractorTest {

    private fun createExtractor(responseText: String): Pair<LlmFactExtractor, FakeFactLlmPort> {
        val fakeLlm = FakeFactLlmPort(response = Either.Right(
            value = LlmResponse(
                content = MessageContent(value = responseText),
                usage = UsageRecord.ZERO,
            )
        ))
        val extractor = LlmFactExtractor(llmPort = fakeLlm)
        return extractor to fakeLlm
    }

    @Test
    fun `extract parses valid JSON response into facts`() = runTest {
        val responseText = """[{"category":"Goal","key":"main_goal","value":"Build a chat bot"},{"category":"Constraint","key":"lang","value":"Kotlin only"}]"""
        val (extractor, _) = createExtractor(responseText = responseText)

        val result = extractor.extract(
            sessionId = AgentSessionId(value = "s1"),
            currentFacts = emptyList(),
            newUserMessage = MessageContent(value = "I want to build a Kotlin chat bot"),
            lastAssistantResponse = null,
        )

        assertEquals(2, result.size)
        assertEquals(FactCategory.Goal, result[0].category)
        assertEquals(FactKey(value = "main_goal"), result[0].key)
        assertEquals(FactValue(value = "Build a chat bot"), result[0].value)
        assertEquals(FactCategory.Constraint, result[1].category)
        assertEquals(FactKey(value = "lang"), result[1].key)
        assertEquals(FactValue(value = "Kotlin only"), result[1].value)
    }

    @Test
    fun `extract sends correct prompt structure with no current facts`() = runTest {
        val responseText = """[{"category":"Goal","key":"goal","value":"test"}]"""
        val (extractor, fakeLlm) = createExtractor(responseText = responseText)

        extractor.extract(
            sessionId = AgentSessionId(value = "s1"),
            currentFacts = emptyList(),
            newUserMessage = MessageContent(value = "Hello"),
            lastAssistantResponse = null,
        )

        val messages = fakeLlm.lastMessages!!

        assertEquals(MessageRole.System, messages[0].role)
        assertTrue(messages[0].content.value.contains("Goal"))
        assertTrue(messages[0].content.value.contains("Constraint"))

        assertEquals(MessageRole.User, messages[1].role)
        assertEquals(MessageContent(value = "Hello"), messages[1].content)

        assertEquals(MessageRole.User, messages[2].role)
        assertTrue(messages[2].content.value.contains("Extract"))

        assertEquals(ResponseFormat.Json, fakeLlm.lastResponseFormat)
    }

    @Test
    fun `extract sends current facts and last assistant response when present`() = runTest {
        val responseText = """[{"category":"Goal","key":"goal","value":"Updated goal"}]"""
        val (extractor, fakeLlm) = createExtractor(responseText = responseText)

        val currentFacts = listOf(
            Fact(sessionId = AgentSessionId(value = "s1"), category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "Old goal")),
        )

        extractor.extract(
            sessionId = AgentSessionId(value = "s1"),
            currentFacts = currentFacts,
            newUserMessage = MessageContent(value = "Actually, change the goal"),
            lastAssistantResponse = MessageContent(value = "Sure, what would you like?"),
        )

        val messages = fakeLlm.lastMessages!!

        assertEquals(MessageRole.System, messages[0].role)

        assertEquals(MessageRole.User, messages[1].role)
        assertTrue(messages[1].content.value.contains("Old goal"))

        assertEquals(MessageRole.Assistant, messages[2].role)
        assertEquals(MessageContent(value = "Sure, what would you like?"), messages[2].content)

        assertEquals(MessageRole.User, messages[3].role)
        assertEquals(MessageContent(value = "Actually, change the goal"), messages[3].content)
    }

    @Test
    fun `extract returns empty list when LLM returns empty array`() = runTest {
        val (extractor, _) = createExtractor(responseText = "[]")

        val result = extractor.extract(
            sessionId = AgentSessionId(value = "s1"),
            currentFacts = emptyList(),
            newUserMessage = MessageContent(value = "Hi"),
            lastAssistantResponse = null,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `extract returns current facts on invalid JSON`() = runTest {
        val (extractor, _) = createExtractor(responseText = "this is not valid json")

        val currentFacts = listOf(
            Fact(sessionId = AgentSessionId(value = "s1"), category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "Keep this")),
        )

        val result = extractor.extract(
            sessionId = AgentSessionId(value = "s1"),
            currentFacts = currentFacts,
            newUserMessage = MessageContent(value = "Something"),
            lastAssistantResponse = null,
        )

        assertEquals(1, result.size)
        assertEquals(FactValue(value = "Keep this"), result[0].value)
    }

    @Test
    fun `extract returns current facts when LLM returns error`() = runTest {
        val fakeLlm = FakeFactLlmPortWithError(error = DomainError.NetworkError(message = "Connection refused"))
        val extractor = LlmFactExtractor(llmPort = fakeLlm)

        val currentFacts = listOf(
            Fact(sessionId = AgentSessionId(value = "s1"), category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "Keep this")),
        )

        val result = extractor.extract(
            sessionId = AgentSessionId(value = "s1"),
            currentFacts = currentFacts,
            newUserMessage = MessageContent(value = "Something"),
            lastAssistantResponse = null,
        )

        assertEquals(1, result.size)
        assertEquals(FactValue(value = "Keep this"), result[0].value)
    }
}

private class FakeFactLlmPort(
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

private class FakeFactLlmPortWithError(
    private val error: DomainError,
) : LlmPort {
    override suspend fun complete(
        messages: List<ContextMessage>,
        responseFormat: ResponseFormat,
    ): Either<DomainError, LlmResponse> = Either.Left(value = error)
}
