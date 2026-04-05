package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.core.agent.Agent
import com.ai.challenge.core.agent.AgentError
import com.ai.challenge.core.agent.AgentResponse
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.metrics.CostDetails
import com.ai.challenge.core.session.SessionId
import com.ai.challenge.core.metrics.TokenDetails
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ChatStoreTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private fun createStore(agent: Agent = FakeAgent()): ChatStore =
        ChatStoreFactory(DefaultStoreFactory(), agent).create()

    @Test
    fun `initial state is empty with no session`() {
        val store = createStore()
        assertEquals(emptyList(), store.state.messages)
        assertFalse(store.state.isLoading)
        assertNull(store.state.sessionId)
        assertEquals(emptyMap(), store.state.turnTokens)
        assertEquals(emptyMap(), store.state.turnCosts)
        assertEquals(TokenDetails(), store.state.sessionTokens)
        assertEquals(CostDetails(), store.state.sessionCosts)
        store.dispose()
    }

    @Test
    fun `LoadSession sets sessionId and loads history as UiMessages`() = runTest {
        val agent = FakeAgent()
        val sessionId = agent.createSession()
        agent.appendTurnDirect(sessionId, Turn(userMessage = "hi", agentResponse = "hello"))
        val store = createStore(agent)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        assertEquals(sessionId, store.state.sessionId)
        assertEquals(2, store.state.messages.size)
        assertEquals("hi", store.state.messages[0].text)
        assertEquals(true, store.state.messages[0].isUser)
        assertEquals("hello", store.state.messages[1].text)
        assertEquals(false, store.state.messages[1].isUser)
        store.dispose()
    }

    @Test
    fun `SendMessage adds user message and agent response`() = runTest {
        val turnId = TurnId.generate()
        val agent = FakeAgent(sendResult = Either.Right(AgentResponse("Hello from agent!", turnId, TokenDetails(), CostDetails())))
        val sessionId = agent.createSession()
        val store = createStore(agent)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()
        store.accept(ChatStore.Intent.SendMessage("Hi"))
        advanceUntilIdle()

        val messages = store.state.messages
        assertEquals(2, messages.size)
        assertEquals("Hi", messages[0].text)
        assertEquals(true, messages[0].isUser)
        assertEquals("Hello from agent!", messages[1].text)
        assertEquals(false, messages[1].isUser)
        assertFalse(store.state.isLoading)
        store.dispose()
    }

    @Test
    fun `SendMessage adds error message on agent failure`() = runTest {
        val agent = FakeAgent(sendResult = Either.Left(AgentError.NetworkError("Timeout")))
        val sessionId = agent.createSession()
        val store = createStore(agent)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()
        store.accept(ChatStore.Intent.SendMessage("Hi"))
        advanceUntilIdle()

        val messages = store.state.messages
        assertEquals(2, messages.size)
        assertEquals(UiMessage("Hi", isUser = true), messages[0])
        assertEquals(UiMessage("Timeout", isUser = false, isError = true), messages[1])
        assertFalse(store.state.isLoading)
        store.dispose()
    }

    @Test
    fun `SendMessage populates turnTokens, turnCosts, sessionTokens, sessionCosts`() = runTest {
        val turnId = TurnId.generate()
        val tokens = TokenDetails(promptTokens = 100, completionTokens = 50)
        val costs = CostDetails(totalCost = 0.001)
        val agent = FakeAgent(sendResult = Either.Right(AgentResponse("Hi!", turnId, tokens, costs)))
        val sessionId = agent.createSession()
        val store = createStore(agent)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()
        store.accept(ChatStore.Intent.SendMessage("Hello"))
        advanceUntilIdle()

        assertEquals(tokens, store.state.turnTokens[turnId])
        assertEquals(costs, store.state.turnCosts[turnId])
        assertEquals(tokens, store.state.sessionTokens)
        assertEquals(costs, store.state.sessionCosts)
        store.dispose()
    }

    @Test
    fun `SendMessage accumulates session metrics across multiple turns`() = runTest {
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        val tokens1 = TokenDetails(promptTokens = 100, completionTokens = 50)
        val costs1 = CostDetails(totalCost = 0.001)
        val tokens2 = TokenDetails(promptTokens = 200, completionTokens = 100)
        val costs2 = CostDetails(totalCost = 0.002)

        var callCount = 0
        val agent = object : FakeAgent() {
            override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> {
                callCount++
                return if (callCount == 1) Either.Right(AgentResponse("r1", turnId1, tokens1, costs1))
                else Either.Right(AgentResponse("r2", turnId2, tokens2, costs2))
            }
        }
        val sessionId = agent.createSession()
        val store = createStore(agent)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()
        store.accept(ChatStore.Intent.SendMessage("Hello"))
        advanceUntilIdle()
        store.accept(ChatStore.Intent.SendMessage("Again"))
        advanceUntilIdle()

        assertEquals(tokens1 + tokens2, store.state.sessionTokens)
        assertEquals(costs1 + costs2, store.state.sessionCosts)
        assertEquals(2, store.state.turnTokens.size)
        store.dispose()
    }

    @Test
    fun `LoadSession loads token and cost data from agent`() = runTest {
        val agent = FakeAgent()
        val sessionId = agent.createSession()

        val turn1 = Turn(userMessage = "a", agentResponse = "b")
        val turn2 = Turn(userMessage = "c", agentResponse = "d")
        val turnId1 = agent.appendTurnDirect(sessionId, turn1)
        val turnId2 = agent.appendTurnDirect(sessionId, turn2)
        agent.recordTokensDirect(sessionId, turnId1, TokenDetails(promptTokens = 10, completionTokens = 5))
        agent.recordTokensDirect(sessionId, turnId2, TokenDetails(promptTokens = 20, completionTokens = 10))
        agent.recordCostsDirect(sessionId, turnId1, CostDetails(totalCost = 0.001))
        agent.recordCostsDirect(sessionId, turnId2, CostDetails(totalCost = 0.002))

        val store = createStore(agent)
        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        assertEquals(TokenDetails(promptTokens = 10, completionTokens = 5), store.state.turnTokens[turnId1])
        assertEquals(TokenDetails(promptTokens = 20, completionTokens = 10), store.state.turnTokens[turnId2])
        assertEquals(TokenDetails(promptTokens = 30, completionTokens = 15), store.state.sessionTokens)
        assertEquals(CostDetails(totalCost = 0.003), store.state.sessionCosts)
        store.dispose()
    }

    @Test
    fun `SendMessage auto-titles session on first message`() = runTest {
        val agent = FakeAgent(sendResult = Either.Right(AgentResponse("response", TurnId.generate(), TokenDetails(), CostDetails())))
        val sessionId = agent.createSession()
        val store = createStore(agent)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()
        store.accept(ChatStore.Intent.SendMessage("Hello world, this is a long message for auto-title testing purposes"))
        advanceUntilIdle()

        val title = agent.getSession(sessionId)?.title ?: ""
        assertEquals("Hello world, this is a long message for auto-title", title)
        store.dispose()
    }
}

open class FakeAgent(
    private val sendResult: Either<AgentError, AgentResponse> = Either.Right(AgentResponse("", TurnId.generate(), TokenDetails(), CostDetails())),
) : Agent {
    private val sessions = ConcurrentHashMap<SessionId, AgentSession>()
    private val turns = ConcurrentHashMap<TurnId, Pair<SessionId, Turn>>()
    private val tokenData = ConcurrentHashMap<TurnId, Pair<SessionId, TokenDetails>>()
    private val costData = ConcurrentHashMap<TurnId, Pair<SessionId, CostDetails>>()

    override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> = sendResult
    override suspend fun createSession(title: String): SessionId {
        val id = SessionId.generate()
        sessions[id] = AgentSession(id = id, title = title)
        return id
    }
    override suspend fun deleteSession(id: SessionId): Boolean = sessions.remove(id) != null
    override suspend fun listSessions(): List<AgentSession> = sessions.values.toList()
    override suspend fun getSession(id: SessionId): AgentSession? = sessions[id]
    override suspend fun updateSessionTitle(id: SessionId, title: String) {
        sessions.computeIfPresent(id) { _, s -> s.copy(title = title) }
    }
    override suspend fun getTurns(sessionId: SessionId, limit: Int?): List<Turn> {
        val all = turns.values.filter { it.first == sessionId }.map { it.second }.sortedBy { it.timestamp }
        return if (limit != null && all.size > limit) all.takeLast(limit) else all
    }
    override suspend fun getTokensByTurn(turnId: TurnId): TokenDetails? = tokenData[turnId]?.second
    override suspend fun getTokensBySession(sessionId: SessionId): Map<TurnId, TokenDetails> =
        tokenData.filter { it.value.first == sessionId }.mapValues { it.value.second }
    override suspend fun getSessionTotalTokens(sessionId: SessionId): TokenDetails =
        getTokensBySession(sessionId).values.fold(TokenDetails()) { acc, t -> acc + t }
    override suspend fun getCostByTurn(turnId: TurnId): CostDetails? = costData[turnId]?.second
    override suspend fun getCostBySession(sessionId: SessionId): Map<TurnId, CostDetails> =
        costData.filter { it.value.first == sessionId }.mapValues { it.value.second }
    override suspend fun getSessionTotalCost(sessionId: SessionId): CostDetails =
        getCostBySession(sessionId).values.fold(CostDetails()) { acc, c -> acc + c }

    fun appendTurnDirect(sessionId: SessionId, turn: Turn): TurnId {
        turns[turn.id] = sessionId to turn
        return turn.id
    }
    fun recordTokensDirect(sessionId: SessionId, turnId: TurnId, details: TokenDetails) {
        tokenData[turnId] = sessionId to details
    }
    fun recordCostsDirect(sessionId: SessionId, turnId: TurnId, details: CostDetails) {
        costData[turnId] = sessionId to details
    }
}
