package com.ai.challenge.fact.extractor

import com.ai.challenge.core.Fact
import com.ai.challenge.core.Turn
import com.ai.challenge.llm.OpenRouterService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LlmFactExtractorTest {

    @Test
    fun `extract parses JSON array response into facts`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"choices":[{"index":0,"message":{"role":"assistant","content":"[\"User prefers Kotlin\",\"Project uses Gradle\"]"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = false }) }
        }
        val service = OpenRouterService(apiKey = "test-key", client = client)
        val extractor = LlmFactExtractor(service = service, model = "test-model")

        val turns = listOf(Turn(userMessage = "I use Kotlin", agentResponse = "Great choice!"))
        val result = extractor.extract(turns, emptyList(), "Tell me about Gradle")

        assertEquals(2, result.size)
        assertEquals("User prefers Kotlin", result[0].content)
        assertEquals("Project uses Gradle", result[1].content)
    }

    @Test
    fun `extract returns current facts on malformed response`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"choices":[{"index":0,"message":{"role":"assistant","content":"not valid json"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = false }) }
        }
        val service = OpenRouterService(apiKey = "test-key", client = client)
        val extractor = LlmFactExtractor(service = service, model = "test-model")

        val existing = listOf(Fact(content = "existing fact"))
        val result = extractor.extract(emptyList(), existing, "hello")

        assertEquals(1, result.size)
        assertEquals("existing fact", result[0].content)
    }
}
