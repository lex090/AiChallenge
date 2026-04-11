package com.ai.challenge.context

import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
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
import kotlin.time.Clock

class LlmContextCompressorTest {

    @Test
    fun `compress sends conversation to LLM and returns summary text`() = runTest {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"choices":[{"index":0,"message":{"role":"assistant","content":"This is a summary."}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = false }) }
        }
        val service = OpenRouterService(apiKey = "test-key", client = client)
        val compressor = LlmContextCompressor(service = service, model = "test-model")

        val turns = listOf(
            Turn(id = TurnId.generate(), userMessage = "Hello", agentResponse = "Hi there!", timestamp = Clock.System.now()),
            Turn(id = TurnId.generate(), userMessage = "How are you?", agentResponse = "I'm fine!", timestamp = Clock.System.now()),
        )

        val result = compressor.compress(turns)

        assertEquals("This is a summary.", result)

        val json = Json.parseToJsonElement(capturedBody!!).jsonObject
        val messages = json["messages"]!!.jsonArray

        // system + 2 user + 2 assistant + final user instruction = 6
        assertEquals(6, messages.size)
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Hello", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("assistant", messages[2].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Hi there!", messages[2].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[3].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("How are you?", messages[3].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("assistant", messages[4].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("I'm fine!", messages[4].jsonObject["content"]!!.jsonPrimitive.content)
        // Last message is the summarization instruction
        assertEquals("user", messages[5].jsonObject["role"]!!.jsonPrimitive.content)
        assertTrue(messages[5].jsonObject["content"]!!.jsonPrimitive.content.contains("summary"))
    }
}
