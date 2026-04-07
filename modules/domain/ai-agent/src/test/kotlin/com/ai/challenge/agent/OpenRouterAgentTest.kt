package com.ai.challenge.agent

import arrow.core.Either
import com.ai.challenge.core.agent.AgentError
import com.ai.challenge.core.agent.AgentResponse
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.context.ContextManagementTypeRepository
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.cost.CostDetails
import com.ai.challenge.core.cost.CostDetailsRepository
import com.ai.challenge.core.context.ContextManager.PreparedContext
import com.ai.challenge.core.context.ContextManager.PreparedContext.ContextMessage
import com.ai.challenge.core.context.ContextManager.PreparedContext.ContextMessage.MessageRole
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.session.AgentSessionRepository
import com.ai.challenge.core.token.TokenDetails
import com.ai.challenge.core.token.TokenDetailsRepository
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.turn.TurnRepository
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenRouterAgentTest {

    private val sessionRepo = FakeSessionRepository()
    private val turnRepo = FakeTurnRepository()
    private val tokenRepo = FakeTokenRepository()
    private val costRepo = FakeCostRepository()
    private val contextManager = PassThroughContextManager(turnRepo)
    private val contextManagementRepo = FakeContextManagementTypeRepository()

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
        OpenRouterService(apiKey = "test-key", client = createMockClient(responseJson))

    private fun createAgent(responseJson: String): AiAgent =
        AiAgent(
            service = createService(responseJson),
            model = "test-model",
            sessionRepository = sessionRepo,
            turnRepository = turnRepo,
            tokenRepository = tokenRepo,
            costRepository = costRepo,
            contextManager = contextManager,
            contextManagementRepository = contextManagementRepo,
        )

    @Test
    fun `send returns Right with response text on success`() = runTest {
        val agent = createAgent("""{"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}""")
        val sessionId = sessionRepo.create()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<AgentResponse>>(result)
        assertEquals("Hello!", result.value.text)
    }

    @Test
    fun `send returns AgentResponse with full token and cost details`() = runTest {
        val agent = createAgent("""{
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
            }""")
        val sessionId = sessionRepo.create()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<AgentResponse>>(result)
        assertEquals(TokenDetails(promptTokens = 100, completionTokens = 50, cachedTokens = 20, cacheWriteTokens = 80, reasoningTokens = 10), result.value.tokenDetails)
        assertEquals(0.0015, result.value.costDetails.totalCost, 1e-9)
        assertEquals(0.0012, result.value.costDetails.upstreamCost, 1e-9)
        assertEquals(0.0008, result.value.costDetails.upstreamPromptCost, 1e-9)
        assertEquals(0.0004, result.value.costDetails.upstreamCompletionsCost, 1e-9)
    }

    @Test
    fun `send returns default details when usage is null`() = runTest {
        val agent = createAgent("""{"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}""")
        val sessionId = sessionRepo.create()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<AgentResponse>>(result)
        assertEquals(TokenDetails(), result.value.tokenDetails)
        assertEquals(CostDetails(), result.value.costDetails)
    }

    @Test
    fun `send persists token and cost details`() = runTest {
        val agent = createAgent("""{
              "choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}],
              "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}
            }""")
        val sessionId = sessionRepo.create()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<AgentResponse>>(result)
        val storedTokens = tokenRepo.getByTurn(result.value.turnId)
        assertNotNull(storedTokens)
        assertEquals(10, storedTokens.promptTokens)
        assertEquals(5, storedTokens.completionTokens)

        val storedCost = costRepo.getByTurn(result.value.turnId)
        assertNotNull(storedCost)
    }

    @Test
    fun `send saves turn on success`() = runTest {
        val agent = createAgent("""{"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}""")
        val sessionId = sessionRepo.create()

        agent.send(sessionId, "Hi")

        val history = turnRepo.getBySession(sessionId)
        assertEquals(1, history.size)
        assertEquals("Hi", history[0].userMessage)
        assertEquals("Hello!", history[0].agentResponse)
    }

    @Test
    fun `send does not save turn on failure`() = runTest {
        val agent = createAgent("""{"error":{"message":"Rate limit exceeded","code":429},"choices":[]}""")
        val sessionId = sessionRepo.create()

        agent.send(sessionId, "Hi")

        val history = turnRepo.getBySession(sessionId)
        assertTrue(history.isEmpty())
    }

    @Test
    fun `send returns Left ApiError when response has error`() = runTest {
        val agent = createAgent("""{"error":{"message":"Rate limit exceeded","code":429},"choices":[]}""")
        val sessionId = sessionRepo.create()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Left<AgentError>>(result)
        assertIs<AgentError.ApiError>(result.value)
        assertEquals("Rate limit exceeded", result.value.message)
    }

    @Test
    fun `send returns Left NetworkError when service throws`() = runTest {
        val mockEngine = MockEngine { _ -> throw RuntimeException("Connection refused") }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = false }) }
        }
        val service = OpenRouterService(apiKey = "test-key", client = client)
        val agent = AiAgent(service, "test-model", sessionRepo, turnRepo, tokenRepo, costRepo, contextManager, contextManagementRepo)
        val sessionId = sessionRepo.create()

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
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = false }) }
        }
        val service = OpenRouterService(apiKey = "test-key", client = client)
        val agent = AiAgent(service, "test-model", sessionRepo, turnRepo, tokenRepo, costRepo, contextManager, contextManagementRepo)
        val sessionId = sessionRepo.create()

        turnRepo.append(sessionId, Turn(userMessage = "Hi", agentResponse = "Hello!"))

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

// --- Test fakes ---

private class FakeSessionRepository : AgentSessionRepository {
    private val sessions = ConcurrentHashMap<AgentSessionId, AgentSession>()

    override suspend fun create(title: String): AgentSessionId {
        val id = AgentSessionId.generate()
        sessions[id] = AgentSession(id = id, title = title)
        return id
    }
    override suspend fun get(id: AgentSessionId): AgentSession? = sessions[id]
    override suspend fun delete(id: AgentSessionId): Boolean = sessions.remove(id) != null
    override suspend fun list(): List<AgentSession> = sessions.values.toList()
    override suspend fun updateTitle(id: AgentSessionId, title: String) {
        sessions.computeIfPresent(id) { _, s -> s.copy(title = title) }
    }
}

private class FakeTurnRepository : TurnRepository {
    private val turns = ConcurrentHashMap<TurnId, Pair<AgentSessionId, Turn>>()

    override suspend fun append(sessionId: AgentSessionId, turn: Turn): TurnId {
        turns[turn.id] = sessionId to turn
        return turn.id
    }
    override suspend fun getBySession(sessionId: AgentSessionId, limit: Int?): List<Turn> {
        val all = turns.values.filter { it.first == sessionId }.map { it.second }.sortedBy { it.timestamp }
        return if (limit != null && all.size > limit) all.takeLast(limit) else all
    }
    override suspend fun get(turnId: TurnId): Turn? = turns[turnId]?.second
}

private class FakeTokenRepository : TokenDetailsRepository {
    private val data = ConcurrentHashMap<TurnId, Pair<AgentSessionId, TokenDetails>>()

    override suspend fun record(sessionId: AgentSessionId, turnId: TurnId, details: TokenDetails) { data[turnId] = sessionId to details }
    override suspend fun getByTurn(turnId: TurnId): TokenDetails? = data[turnId]?.second
    override suspend fun getBySession(sessionId: AgentSessionId): Map<TurnId, TokenDetails> =
        data.filter { it.value.first == sessionId }.mapValues { it.value.second }
    override suspend fun getSessionTotal(sessionId: AgentSessionId): TokenDetails =
        getBySession(sessionId).values.fold(TokenDetails()) { acc, t -> acc + t }
}

private class FakeCostRepository : CostDetailsRepository {
    private val data = ConcurrentHashMap<TurnId, Pair<AgentSessionId, CostDetails>>()

    override suspend fun record(sessionId: AgentSessionId, turnId: TurnId, details: CostDetails) { data[turnId] = sessionId to details }
    override suspend fun getByTurn(turnId: TurnId): CostDetails? = data[turnId]?.second
    override suspend fun getBySession(sessionId: AgentSessionId): Map<TurnId, CostDetails> =
        data.filter { it.value.first == sessionId }.mapValues { it.value.second }
    override suspend fun getSessionTotal(sessionId: AgentSessionId): CostDetails =
        getBySession(sessionId).values.fold(CostDetails()) { acc, c -> acc + c }
}

private class FakeContextManagementTypeRepository : ContextManagementTypeRepository {
    private val store = mutableMapOf<AgentSessionId, ContextManagementType>()

    override suspend fun save(sessionId: AgentSessionId, type: ContextManagementType) {
        store[sessionId] = type
    }

    override suspend fun getBySession(sessionId: AgentSessionId): ContextManagementType =
        store[sessionId] ?: ContextManagementType.None

    override suspend fun delete(sessionId: AgentSessionId) {
        store.remove(sessionId)
    }
}

private class PassThroughContextManager(
    private val turnRepo: TurnRepository,
) : ContextManager {
    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        newMessage: String,
    ): PreparedContext {
        val history = turnRepo.getBySession(sessionId)
        val messages = buildList {
            for (turn in history) {
                add(ContextMessage(MessageRole.User, turn.userMessage))
                add(ContextMessage(MessageRole.Assistant, turn.agentResponse))
            }
            add(ContextMessage(MessageRole.User, newMessage))
        }
        return PreparedContext(
            messages = messages,
            compressed = false,
            originalTurnCount = history.size,
            retainedTurnCount = history.size,
            summaryCount = 0,
        )
    }
}
