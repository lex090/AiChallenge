package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.agent.Agent
import com.ai.challenge.agent.AgentError
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
    fun `initial state is empty`() {
        val store = ChatStoreFactory(DefaultStoreFactory(), FakeAgent()).create()

        assertEquals(emptyList(), store.state.messages)
        assertFalse(store.state.isLoading)

        store.dispose()
    }

    @Test
    fun `SendMessage adds user message and agent response`() = runTest {
        val agent = FakeAgent(response = Either.Right("Hello from agent!"))
        val store = ChatStoreFactory(DefaultStoreFactory(), agent).create()

        store.accept(ChatStore.Intent.SendMessage("Hi"))

        // Wait for coroutine to complete
        advanceUntilIdle()

        val messages = store.state.messages
        assertEquals(2, messages.size)
        assertEquals(UiMessage("Hi", isUser = true), messages[0])
        assertEquals(UiMessage("Hello from agent!", isUser = false), messages[1])
        assertFalse(store.state.isLoading)

        store.dispose()
    }

    @Test
    fun `SendMessage adds error message on agent failure`() = runTest {
        val agent = FakeAgent(response = Either.Left(AgentError.NetworkError("Timeout")))
        val store = ChatStoreFactory(DefaultStoreFactory(), agent).create()

        store.accept(ChatStore.Intent.SendMessage("Hi"))

        advanceUntilIdle()

        val messages = store.state.messages
        assertEquals(2, messages.size)
        assertEquals(UiMessage("Hi", isUser = true), messages[0])
        assertEquals(UiMessage("Timeout", isUser = false, isError = true), messages[1])
        assertFalse(store.state.isLoading)

        store.dispose()
    }
}

class FakeAgent(
    private val response: Either<AgentError, String> = Either.Right(""),
) : Agent {
    override suspend fun send(message: String): Either<AgentError, String> = response
}
