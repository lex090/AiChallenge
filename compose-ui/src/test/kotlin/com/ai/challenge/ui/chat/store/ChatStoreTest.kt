package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.agent.Agent
import com.ai.challenge.agent.AgentError
import com.ai.challenge.agent.AgentResponse
import com.ai.challenge.session.TokenUsage
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.InMemorySessionManager
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.Turn
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

    @Test
    fun `initial state is empty with no session`() {
        val sessionManager = InMemorySessionManager()
        val store = ChatStoreFactory(DefaultStoreFactory(), FakeAgent(), sessionManager).create()

        assertEquals(emptyList(), store.state.messages)
        assertFalse(store.state.isLoading)
        assertNull(store.state.sessionId)

        store.dispose()
    }

    @Test
    fun `LoadSession sets sessionId and loads history as UiMessages`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        sessionManager.appendTurn(sessionId, Turn(userMessage = "hi", agentResponse = "hello"))

        val store = ChatStoreFactory(DefaultStoreFactory(), FakeAgent(), sessionManager).create()

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        assertEquals(sessionId, store.state.sessionId)
        assertEquals(2, store.state.messages.size)
        assertEquals(UiMessage("hi", isUser = true, tokenUsage = TokenUsage()), store.state.messages[0])
        assertEquals(UiMessage("hello", isUser = false, tokenUsage = TokenUsage()), store.state.messages[1])

        store.dispose()
    }

    @Test
    fun `SendMessage adds user message and agent response`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val agent = FakeAgent(response = Either.Right(AgentResponse("Hello from agent!", TokenUsage())))
        val store = ChatStoreFactory(DefaultStoreFactory(), agent, sessionManager).create()

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hi"))
        advanceUntilIdle()

        val messages = store.state.messages
        assertEquals(2, messages.size)
        assertEquals(UiMessage("Hi", isUser = true, tokenUsage = TokenUsage()), messages[0])
        assertEquals(UiMessage("Hello from agent!", isUser = false, tokenUsage = TokenUsage()), messages[1])
        assertFalse(store.state.isLoading)

        store.dispose()
    }

    @Test
    fun `SendMessage adds error message on agent failure`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val agent = FakeAgent(response = Either.Left(AgentError.NetworkError("Timeout")))
        val store = ChatStoreFactory(DefaultStoreFactory(), agent, sessionManager).create()

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
    fun `SendMessage auto-titles session on first message`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val agent = FakeAgent(response = Either.Right(AgentResponse("response", TokenUsage())))
        val store = ChatStoreFactory(DefaultStoreFactory(), agent, sessionManager).create()

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hello world, this is a long message for auto-title testing purposes"))
        advanceUntilIdle()

        val title = sessionManager.getSession(sessionId)?.title ?: ""
        assertEquals("Hello world, this is a long message for auto-title", title) // 50 chars

        store.dispose()
    }

    @Test
    fun `SendMessage populates tokenUsage on user and agent messages`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val usage = TokenUsage(promptTokens = 100, completionTokens = 50, totalTokens = 150)
        val agent = FakeAgent(response = Either.Right(AgentResponse(text = "Hi!", tokenUsage = usage)))
        val store = ChatStoreFactory(DefaultStoreFactory(), agent, sessionManager).create()

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hello"))
        advanceUntilIdle()

        val messages = store.state.messages
        assertEquals(2, messages.size)
        assertEquals(usage, messages[0].tokenUsage)
        assertEquals(usage, messages[1].tokenUsage)
    }

    @Test
    fun `SendMessage accumulates sessionTokens`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val usage = TokenUsage(promptTokens = 100, completionTokens = 50, totalTokens = 150)
        val agent = FakeAgent(response = Either.Right(AgentResponse(text = "Hi!", tokenUsage = usage)))
        val store = ChatStoreFactory(DefaultStoreFactory(), agent, sessionManager).create()

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hello"))
        advanceUntilIdle()

        assertEquals(usage, store.state.sessionTokens)
    }

    @Test
    fun `LoadSession computes sessionTokens from history`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        sessionManager.appendTurn(sessionId, Turn(userMessage = "a", agentResponse = "b", tokenUsage = TokenUsage(10, 5, 15)))
        sessionManager.appendTurn(sessionId, Turn(userMessage = "c", agentResponse = "d", tokenUsage = TokenUsage(20, 10, 30)))

        val store = ChatStoreFactory(DefaultStoreFactory(), FakeAgent(), sessionManager).create()

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        assertEquals(TokenUsage(promptTokens = 30, completionTokens = 15, totalTokens = 45), store.state.sessionTokens)
    }
}

class FakeAgent(
    private val response: Either<AgentError, AgentResponse> = Either.Right(AgentResponse("", TokenUsage())),
) : Agent {
    override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> = response
}
