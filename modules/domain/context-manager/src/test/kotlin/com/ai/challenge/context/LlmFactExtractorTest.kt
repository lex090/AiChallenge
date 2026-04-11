package com.ai.challenge.context

import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactId
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.llm.OpenRouterService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LlmFactExtractorTest {

    private fun createExtractor(responseJson: String): Pair<LlmFactExtractor, () -> String?> {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"choices":[{"index":0,"message":{"role":"assistant","content":${Json.encodeToString(responseJson)}}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = false }) }
        }
        val service = OpenRouterService(apiKey = "test-key", client = client)
        val extractor = LlmFactExtractor(service = service, model = "test-model")
        return extractor to { capturedBody }
    }

    @Test
    fun `extract parses valid JSON response into facts`() = runTest {
        val responseJson = """[{"category":"Goal","key":"main_goal","value":"Build a chat bot"},{"category":"Constraint","key":"lang","value":"Kotlin only"}]"""
        val (extractor, _) = createExtractor(responseJson = responseJson)

        val result = extractor.extract(
            sessionId = AgentSessionId(value = "s1"),
            currentFacts = emptyList(),
            newUserMessage = "I want to build a Kotlin chat bot",
            lastAssistantResponse = null,
        )

        assertEquals(2, result.size)
        assertEquals(FactCategory.Goal, result[0].category)
        assertEquals("main_goal", result[0].key)
        assertEquals("Build a chat bot", result[0].value)
        assertEquals(FactCategory.Constraint, result[1].category)
        assertEquals("lang", result[1].key)
        assertEquals("Kotlin only", result[1].value)
    }

    @Test
    fun `extract sends correct prompt structure with no current facts`() = runTest {
        val responseJson = """[{"category":"Goal","key":"goal","value":"test"}]"""
        val (extractor, getCapturedBody) = createExtractor(responseJson = responseJson)

        extractor.extract(
            sessionId = AgentSessionId(value = "s1"),
            currentFacts = emptyList(),
            newUserMessage = "Hello",
            lastAssistantResponse = null,
        )

        val json = Json.parseToJsonElement(getCapturedBody()!!).jsonObject
        val messages = json["messages"]!!.jsonArray

        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertTrue(messages[0].jsonObject["content"]!!.jsonPrimitive.content.contains("Goal"))
        assertTrue(messages[0].jsonObject["content"]!!.jsonPrimitive.content.contains("Constraint"))

        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Hello", messages[1].jsonObject["content"]!!.jsonPrimitive.content)

        assertEquals("user", messages[2].jsonObject["role"]!!.jsonPrimitive.content)
        assertTrue(messages[2].jsonObject["content"]!!.jsonPrimitive.content.contains("Extract"))
    }

    @Test
    fun `extract sends current facts and last assistant response when present`() = runTest {
        val responseJson = """[{"category":"Goal","key":"goal","value":"Updated goal"}]"""
        val (extractor, getCapturedBody) = createExtractor(responseJson = responseJson)

        val currentFacts = listOf(
            Fact(id = FactId.generate(), sessionId = AgentSessionId(value = "s1"), category = FactCategory.Goal, key = "goal", value = "Old goal"),
        )

        extractor.extract(
            sessionId = AgentSessionId(value = "s1"),
            currentFacts = currentFacts,
            newUserMessage = "Actually, change the goal",
            lastAssistantResponse = "Sure, what would you like?",
        )

        val json = Json.parseToJsonElement(getCapturedBody()!!).jsonObject
        val messages = json["messages"]!!.jsonArray

        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)

        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertTrue(messages[1].jsonObject["content"]!!.jsonPrimitive.content.contains("Old goal"))

        assertEquals("assistant", messages[2].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Sure, what would you like?", messages[2].jsonObject["content"]!!.jsonPrimitive.content)

        assertEquals("user", messages[3].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Actually, change the goal", messages[3].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `extract returns empty list when LLM returns empty array`() = runTest {
        val (extractor, _) = createExtractor(responseJson = "[]")

        val result = extractor.extract(
            sessionId = AgentSessionId(value = "s1"),
            currentFacts = emptyList(),
            newUserMessage = "Hi",
            lastAssistantResponse = null,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `extract returns current facts on invalid JSON`() = runTest {
        val (extractor, _) = createExtractor(responseJson = "this is not valid json")

        val currentFacts = listOf(
            Fact(id = FactId.generate(), sessionId = AgentSessionId(value = "s1"), category = FactCategory.Goal, key = "goal", value = "Keep this"),
        )

        val result = extractor.extract(
            sessionId = AgentSessionId(value = "s1"),
            currentFacts = currentFacts,
            newUserMessage = "Something",
            lastAssistantResponse = null,
        )

        assertEquals(1, result.size)
        assertEquals("Keep this", result[0].value)
    }
}
