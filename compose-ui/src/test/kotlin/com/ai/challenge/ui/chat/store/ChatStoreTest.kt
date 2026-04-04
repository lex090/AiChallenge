package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.agent.Agent
import com.ai.challenge.agent.AgentError
import com.ai.challenge.agent.AgentResponse
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.CostDetails
import com.ai.challenge.session.InMemorySessionManager
import com.ai.challenge.session.InMemoryUsageManager
import com.ai.challenge.session.RequestMetrics
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.TokenDetails
import com.ai.challenge.session.Turn
import com.ai.challenge.session.TurnId
import com.ai.challenge.session.UsageManager
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ChatStoreTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createStore(
        agent: Agent = FakeAgent(),
        sessionManager: AgentSessionManager = InMemorySessionManager(),
        usageManager: UsageManager = InMemoryUsageManager(sessionManager),
    ): ChatStore = ChatStoreFactory(DefaultStoreFactory(), agent, sessionManager, usageManager).create()

    @Test
    fun `initial state is empty with no session`() {
        val store = createStore()

        assertEquals(emptyList(), store.state.messages)
        assertFalse(store.state.isLoading)
        assertNull(store.state.sessionId)
        assertEquals(emptyMap(), store.state.turnMetrics)
        assertEquals(RequestMetrics(), store.state.sessionMetrics)

        store.dispose()
    }

    @Test
    fun `LoadSession sets sessionId and loads history as UiMessages`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        sessionManager.appendTurn(sessionId, Turn(userMessage = "hi", agentResponse = "hello"))
        val store = createStore(sessionManager = sessionManager)

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
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val turnId = TurnId.generate()
        val agent = FakeAgent(response = Either.Right(AgentResponse("Hello from agent!", turnId, RequestMetrics())))
        val store = createStore(agent = agent, sessionManager = sessionManager)

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
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val agent = FakeAgent(response = Either.Left(AgentError.NetworkError("Timeout")))
        val store = createStore(agent = agent, sessionManager = sessionManager)

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
    fun `SendMessage populates turnMetrics and sessionMetrics`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val turnId = TurnId.generate()
        val metrics = RequestMetrics(
            tokens = TokenDetails(promptTokens = 100, completionTokens = 50),
            cost = CostDetails(totalCost = 0.001),
        )
        val agent = FakeAgent(response = Either.Right(AgentResponse(text = "Hi!", turnId = turnId, metrics = metrics)))
        val store = createStore(agent = agent, sessionManager = sessionManager)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hello"))
        advanceUntilIdle()

        assertEquals(metrics, store.state.turnMetrics[turnId])
        assertEquals(metrics, store.state.sessionMetrics)

        store.dispose()
    }

    @Test
    fun `SendMessage accumulates sessionMetrics across multiple turns`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        val metrics1 = RequestMetrics(tokens = TokenDetails(promptTokens = 100, completionTokens = 50), cost = CostDetails(totalCost = 0.001))
        val metrics2 = RequestMetrics(tokens = TokenDetails(promptTokens = 200, completionTokens = 100), cost = CostDetails(totalCost = 0.002))

        var callCount = 0
        val agent = object : Agent {
            override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> {
                callCount++
                return if (callCount == 1) {
                    Either.Right(AgentResponse("r1", turnId1, metrics1))
                } else {
                    Either.Right(AgentResponse("r2", turnId2, metrics2))
                }
            }
        }
        val store = createStore(agent = agent, sessionManager = sessionManager)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hello"))
        advanceUntilIdle()
        store.accept(ChatStore.Intent.SendMessage("Again"))
        advanceUntilIdle()

        assertEquals(metrics1 + metrics2, store.state.sessionMetrics)
        assertEquals(2, store.state.turnMetrics.size)

        store.dispose()
    }

    @Test
    fun `LoadSession loads turnMetrics from usageManager`() = runTest {
        val sessionManager = InMemorySessionManager()
        val usageManager = InMemoryUsageManager(sessionManager)
        val sessionId = sessionManager.createSession()

        val turn1 = Turn(userMessage = "a", agentResponse = "b")
        val turn2 = Turn(userMessage = "c", agentResponse = "d")
        val turnId1 = sessionManager.appendTurn(sessionId, turn1)
        val turnId2 = sessionManager.appendTurn(sessionId, turn2)
        val m1 = RequestMetrics(tokens = TokenDetails(promptTokens = 10, completionTokens = 5), cost = CostDetails(totalCost = 0.001))
        val m2 = RequestMetrics(tokens = TokenDetails(promptTokens = 20, completionTokens = 10), cost = CostDetails(totalCost = 0.002))
        usageManager.record(turnId1, m1)
        usageManager.record(turnId2, m2)

        val store = createStore(sessionManager = sessionManager, usageManager = usageManager)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        assertEquals(m1, store.state.turnMetrics[turnId1])
        assertEquals(m2, store.state.turnMetrics[turnId2])
        assertEquals(m1 + m2, store.state.sessionMetrics)

        store.dispose()
    }

    @Test
    fun `SendMessage auto-titles session on first message`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val agent = FakeAgent(response = Either.Right(AgentResponse("response", TurnId.generate(), RequestMetrics())))
        val store = createStore(agent = agent, sessionManager = sessionManager)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hello world, this is a long message for auto-title testing purposes"))
        advanceUntilIdle()

        val title = sessionManager.getSession(sessionId)?.title ?: ""
        assertEquals("Hello world, this is a long message for auto-title", title)

        store.dispose()
    }
}

class FakeAgent(
    private val response: Either<AgentError, AgentResponse> = Either.Right(AgentResponse("", TurnId.generate(), RequestMetrics())),
) : Agent {
    override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> = response
}
