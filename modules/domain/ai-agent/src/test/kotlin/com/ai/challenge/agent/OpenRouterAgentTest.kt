package com.ai.challenge.agent

import arrow.core.Either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.branch.TurnSequence
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.context.PreparedContext
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.shared.UpdatedAt
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.model.Cost
import com.ai.challenge.core.usage.model.TokenCount
import com.ai.challenge.core.usage.model.UsageRecord
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
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

class AiChatServiceTest {

    private val sessionRepo = FakeAgentSessionRepository()
    private val contextManager = PassThroughContextManager(sessionRepo = sessionRepo)

    private suspend fun createTestSession(): Pair<AgentSessionId, BranchId> {
        val mainBranchId = BranchId.generate()
        val now = Clock.System.now()
        val session = AgentSession(
            id = AgentSessionId.generate(),
            title = SessionTitle(value = ""),
            contextManagementType = ContextManagementType.None,
            createdAt = CreatedAt(value = now),
            updatedAt = UpdatedAt(value = now),
        )
        sessionRepo.save(session = session)
        sessionRepo.createBranch(branch = Branch(
            id = mainBranchId,
            sessionId = session.id,
            sourceTurnId = null,
            turnSequence = TurnSequence(values = emptyList()),
            createdAt = CreatedAt(value = now),
        ))
        return session.id to mainBranchId
    }

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
        OpenRouterService(apiKey = "test-key", client = createMockClient(responseJson = responseJson))

    private fun createChatService(responseJson: String): AiChatService =
        AiChatService(
            service = createService(responseJson = responseJson),
            model = "test-model",
            repository = sessionRepo,
            contextManager = contextManager,
        )

    @Test
    fun `send returns Right with Turn on success`() = runTest {
        val chatService = createChatService(responseJson = """{"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}""")
        val (sessionId, branchId) = createTestSession()

        val result = chatService.send(sessionId = sessionId, branchId = branchId, message = MessageContent(value = "Hi"))

        assertIs<Either.Right<Turn>>(result)
        assertEquals(MessageContent(value = "Hello!"), result.value.assistantMessage)
    }

    @Test
    fun `send returns Turn with usage record`() = runTest {
        val chatService = createChatService(responseJson = """{
              "choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}],
              "usage":{
                "prompt_tokens":100,"completion_tokens":50,"total_tokens":150,
                "prompt_tokens_details":{"cached_tokens":20,"cache_write_tokens":80},
                "completion_tokens_details":{"reasoning_tokens":10},
                "cost":0.0015
              }
            }""")
        val (sessionId, branchId) = createTestSession()

        val result = chatService.send(sessionId = sessionId, branchId = branchId, message = MessageContent(value = "Hi"))

        assertIs<Either.Right<Turn>>(result)
        assertEquals(TokenCount(value = 100), result.value.usage.promptTokens)
        assertEquals(TokenCount(value = 50), result.value.usage.completionTokens)
        assertEquals(TokenCount(value = 20), result.value.usage.cachedTokens)
        assertEquals(TokenCount(value = 80), result.value.usage.cacheWriteTokens)
        assertEquals(TokenCount(value = 10), result.value.usage.reasoningTokens)
    }

    @Test
    fun `send saves turn on success`() = runTest {
        val chatService = createChatService(responseJson = """{"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}""")
        val (sessionId, branchId) = createTestSession()

        chatService.send(sessionId = sessionId, branchId = branchId, message = MessageContent(value = "Hi"))

        val turns = sessionRepo.getTurnsByBranch(branchId = branchId)
        assertEquals(1, turns.size)
        assertEquals(MessageContent(value = "Hi"), turns[0].userMessage)
        assertEquals(MessageContent(value = "Hello!"), turns[0].assistantMessage)
    }

    @Test
    fun `send does not save turn on failure`() = runTest {
        val chatService = createChatService(responseJson = """{"error":{"message":"Rate limit exceeded","code":429},"choices":[]}""")
        val (sessionId, branchId) = createTestSession()

        chatService.send(sessionId = sessionId, branchId = branchId, message = MessageContent(value = "Hi"))

        val turns = sessionRepo.getTurnsByBranch(branchId = branchId)
        assertTrue(turns.isEmpty())
    }

    @Test
    fun `send returns Left ApiError when response has error`() = runTest {
        val chatService = createChatService(responseJson = """{"error":{"message":"Rate limit exceeded","code":429},"choices":[]}""")
        val (sessionId, branchId) = createTestSession()

        val result = chatService.send(sessionId = sessionId, branchId = branchId, message = MessageContent(value = "Hi"))

        assertIs<Either.Left<DomainError>>(result)
        assertIs<DomainError.ApiError>(result.value)
        assertEquals("Rate limit exceeded", result.value.message)
    }

    @Test
    fun `send returns Left NetworkError when service throws`() = runTest {
        val mockEngine = MockEngine { _ -> throw RuntimeException("Connection refused") }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = false }) }
        }
        val service = OpenRouterService(apiKey = "test-key", client = client)
        val chatService = AiChatService(
            service = service,
            model = "test-model",
            repository = sessionRepo,
            contextManager = contextManager,
        )
        val (sessionId, branchId) = createTestSession()

        val result = chatService.send(sessionId = sessionId, branchId = branchId, message = MessageContent(value = "Hi"))

        assertIs<Either.Left<DomainError>>(result)
        assertIs<DomainError.NetworkError>(result.value)
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
        val chatService = AiChatService(
            service = service,
            model = "test-model",
            repository = sessionRepo,
            contextManager = contextManager,
        )
        val (sessionId, branchId) = createTestSession()

        val turn = Turn(
            id = TurnId.generate(),
            sessionId = sessionId,
            userMessage = MessageContent(value = "Hi"),
            assistantMessage = MessageContent(value = "Hello!"),
            usage = ZERO_USAGE,
            createdAt = CreatedAt(value = Clock.System.now()),
        )
        sessionRepo.appendTurn(branchId = branchId, turn = turn)

        chatService.send(sessionId = sessionId, branchId = branchId, message = MessageContent(value = "Remember me?"))

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

private val ZERO_USAGE = UsageRecord(
    promptTokens = TokenCount(value = 0),
    completionTokens = TokenCount(value = 0),
    cachedTokens = TokenCount(value = 0),
    cacheWriteTokens = TokenCount(value = 0),
    reasoningTokens = TokenCount(value = 0),
    totalCost = Cost(value = BigDecimal.ZERO),
    upstreamCost = Cost(value = BigDecimal.ZERO),
    upstreamPromptCost = Cost(value = BigDecimal.ZERO),
    upstreamCompletionsCost = Cost(value = BigDecimal.ZERO),
)

private class FakeAgentSessionRepository : AgentSessionRepository {
    private val sessions = ConcurrentHashMap<AgentSessionId, AgentSession>()
    private val branches = ConcurrentHashMap<BranchId, Branch>()
    private val turnsByBranch = ConcurrentHashMap<BranchId, MutableList<Turn>>()
    private val turnsById = ConcurrentHashMap<TurnId, Turn>()

    override suspend fun save(session: AgentSession): AgentSession {
        sessions[session.id] = session
        return session
    }
    override suspend fun get(id: AgentSessionId): AgentSession? = sessions[id]
    override suspend fun delete(id: AgentSessionId) { sessions.remove(id) }
    override suspend fun list(): List<AgentSession> = sessions.values.toList()
    override suspend fun update(session: AgentSession): AgentSession {
        sessions[session.id] = session
        return session
    }

    override suspend fun createBranch(branch: Branch): Branch {
        branches[branch.id] = branch
        turnsByBranch.putIfAbsent(branch.id, mutableListOf())
        return branch
    }
    override suspend fun getBranches(sessionId: AgentSessionId): List<Branch> =
        branches.values.filter { it.sessionId == sessionId }
    override suspend fun getBranch(branchId: BranchId): Branch? = branches[branchId]
    override suspend fun getMainBranch(sessionId: AgentSessionId): Branch? =
        branches.values.firstOrNull { it.sessionId == sessionId && it.isMain }
    override suspend fun deleteBranch(branchId: BranchId) { branches.remove(branchId) }
    override suspend fun deleteTurnsByBranch(branchId: BranchId) { turnsByBranch[branchId]?.clear() }

    override suspend fun appendTurn(branchId: BranchId, turn: Turn): Turn {
        turnsById[turn.id] = turn
        turnsByBranch.getOrPut(branchId) { mutableListOf() }.add(turn)
        val branch = branches[branchId]
        if (branch != null) {
            branches[branchId] = branch.copy(turnSequence = TurnSequence(values = branch.turnSequence.values + turn.id))
        }
        return turn
    }
    override suspend fun getTurnsByBranch(branchId: BranchId): List<Turn> {
        val branch = branches[branchId] ?: return emptyList()
        return branch.turnSequence.values.mapNotNull { turnsById[it] }
    }
    override suspend fun getTurn(turnId: TurnId): Turn? = turnsById[turnId]
}

private class PassThroughContextManager(
    private val sessionRepo: AgentSessionRepository,
) : ContextManager {
    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
    ): PreparedContext {
        val history = sessionRepo.getTurnsByBranch(branchId = branchId)
        val messages = buildList {
            for (turn in history) {
                add(ContextMessage(role = MessageRole.User, content = turn.userMessage))
                add(ContextMessage(role = MessageRole.Assistant, content = turn.assistantMessage))
            }
            add(ContextMessage(role = MessageRole.User, content = newMessage))
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
