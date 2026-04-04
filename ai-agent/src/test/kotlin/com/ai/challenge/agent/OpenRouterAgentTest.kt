package com.ai.challenge.agent

import arrow.core.Either
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.session.CostDetails
import com.ai.challenge.session.InMemorySessionManager
import com.ai.challenge.session.InMemoryUsageManager
import com.ai.challenge.session.RequestMetrics
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.TokenDetails
import com.ai.challenge.session.Turn
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenRouterAgentTest {

    private val sessionManager = InMemorySessionManager()
    private val usageManager = InMemoryUsageManager(sessionManager)

    private fun createMockClient(responseJson: String): HttpClient {
        val mockEngine = MockEngine { _ ->
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
            }
        }
    }

    private fun createService(responseJson: String): OpenRouterService =
        OpenRouterService(
            apiKey = "test-key",
            client = createMockClient(responseJson),
        )

    private fun createAgent(responseJson: String): OpenRouterAgent =
        OpenRouterAgent(
            service = createService(responseJson),
            model = "test-model",
            sessionManager = sessionManager,
            usageManager = usageManager,
        )

    @Test
    fun `send returns Right with response text on success`() = runTest {
        val agent = createAgent("""
            {"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}
        """.trimIndent())
        val sessionId = sessionManager.createSession()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<AgentResponse>>(result)
        assertEquals("Hello!", result.value.text)
    }

    @Test
    fun `send returns AgentResponse with full metrics`() = runTest {
        val agent = createAgent("""
            {
              "choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}],
              "usage":{
                "prompt_tokens":100,"completion_tokens":50,"total_tokens":150,
                "prompt_tokens_details":{"cached_tokens":20,"cache_write_tokens":80},
                "completion_tokens_details":{"reasoning_tokens":10},
                "cost":0.0015
              },
              "cost":0.0015,
              "cost_details":{
                "upstream_inference_cost":0.0012,
                "upstream_inference_prompt_cost":0.0008,
                "upstream_inference_completions_cost":0.0004
              }
            }
        """.trimIndent())
        val sessionId = sessionManager.createSession()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<AgentResponse>>(result)
        val metrics = result.value.metrics
        assertEquals(TokenDetails(promptTokens = 100, completionTokens = 50, cachedTokens = 20, cacheWriteTokens = 80, reasoningTokens = 10), metrics.tokens)
        assertEquals(0.0015, metrics.cost.totalCost, 1e-9)
        assertEquals(0.0012, metrics.cost.upstreamCost, 1e-9)
        assertEquals(0.0008, metrics.cost.upstreamPromptCost, 1e-9)
        assertEquals(0.0004, metrics.cost.upstreamCompletionsCost, 1e-9)
    }

    @Test
    fun `send returns default RequestMetrics when usage is null`() = runTest {
        val agent = createAgent("""
            {"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}
        """.trimIndent())
        val sessionId = sessionManager.createSession()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<AgentResponse>>(result)
        assertEquals(RequestMetrics(), result.value.metrics)
    }

    @Test
    fun `send persists metrics via usageManager`() = runTest {
        val agent = createAgent("""
            {
              "choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}],
              "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}
            }
        """.trimIndent())
        val sessionId = sessionManager.createSession()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<AgentResponse>>(result)
        val stored = usageManager.getByTurn(result.value.turnId)
        assertNotNull(stored)
        assertEquals(10, stored.tokens.promptTokens)
        assertEquals(5, stored.tokens.completionTokens)
    }

    @Test
    fun `send saves turn to session on success`() = runTest {
        val agent = createAgent("""
            {"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}
        """.trimIndent())
        val sessionId = sessionManager.createSession()

        agent.send(sessionId, "Hi")

        val history = sessionManager.getHistory(sessionId)
        assertEquals(1, history.size)
        assertEquals("Hi", history[0].userMessage)
        assertEquals("Hello!", history[0].agentResponse)
    }

    @Test
    fun `send does not save turn on failure`() = runTest {
        val agent = createAgent("""
            {"error":{"message":"Rate limit exceeded","code":429},"choices":[]}
        """.trimIndent())
        val sessionId = sessionManager.createSession()

        agent.send(sessionId, "Hi")

        val history = sessionManager.getHistory(sessionId)
        assertTrue(history.isEmpty())
    }

    @Test
    fun `send returns Left ApiError when response has error`() = runTest {
        val agent = createAgent("""
            {"error":{"message":"Rate limit exceeded","code":429},"choices":[]}
        """.trimIndent())
        val sessionId = sessionManager.createSession()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Left<AgentError>>(result)
        assertIs<AgentError.ApiError>(result.value)
        assertEquals("Rate limit exceeded", result.value.message)
    }

    @Test
    fun `send returns Left NetworkError when service throws`() = runTest {
        val mockEngine = MockEngine { _ ->
            throw RuntimeException("Connection refused")
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
            }
        }
        val service = OpenRouterService(apiKey = "test-key", client = client)
        val agent = OpenRouterAgent(service, model = "test-model", sessionManager = sessionManager, usageManager = usageManager)
        val sessionId = sessionManager.createSession()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Left<AgentError>>(result)
        assertIs<AgentError.NetworkError>(result.value)
        assertEquals("Connection refused", result.value.message)
    }

    @Test
    fun `send includes history in LLM request`() = runTest {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"choices":[{"index":0,"message":{"role":"assistant","content":"I remember!"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
            }
        }
        val service = OpenRouterService(apiKey = "test-key", client = client)
        val agent = OpenRouterAgent(service, model = "test-model", sessionManager = sessionManager, usageManager = usageManager)
        val sessionId = sessionManager.createSession()

        sessionManager.appendTurn(sessionId, Turn(userMessage = "Hi", agentResponse = "Hello!"))

        agent.send(sessionId, "Remember me?")

        val json = Json.parseToJsonElement(capturedBody!!).jsonObject
        val messages = json["messages"]!!.jsonArray
        assertEquals(3, messages.size)
        assertEquals("user", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Hi", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("assistant", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Hello!", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[2].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Remember me?", messages[2].jsonObject["content"]!!.jsonPrimitive.content)
    }
}
