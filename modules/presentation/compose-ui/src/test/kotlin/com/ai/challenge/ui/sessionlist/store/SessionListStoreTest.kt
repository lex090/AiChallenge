package com.ai.challenge.ui.sessionlist.store

import arrow.core.Either
import com.ai.challenge.ui.chat.store.FakeAgent
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionListStoreTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `initial state has empty session list`() {
        val agent = FakeAgent()
        val store = SessionListStoreFactory(storeFactory = DefaultStoreFactory(), sessionManager = agent).create()
        assertTrue(store.state.sessions.isEmpty())
        assertNull(store.state.activeSessionId)
        store.dispose()
    }

    @Test
    fun `LoadSessions populates session list`() = runTest {
        val agent = FakeAgent()
        (agent.createSession(title = "Chat 1") as Either.Right).value
        (agent.createSession(title = "Chat 2") as Either.Right).value

        val store = SessionListStoreFactory(storeFactory = DefaultStoreFactory(), sessionManager = agent).create()
        store.accept(SessionListStore.Intent.LoadSessions)
        advanceUntilIdle()

        assertEquals(2, store.state.sessions.size)
        store.dispose()
    }

    @Test
    fun `CreateSession creates new session and makes it active`() = runTest {
        val agent = FakeAgent()
        val store = SessionListStoreFactory(storeFactory = DefaultStoreFactory(), sessionManager = agent).create()

        store.accept(SessionListStore.Intent.CreateSession)
        advanceUntilIdle()

        assertEquals(1, store.state.sessions.size)
        assertNotNull(store.state.activeSessionId)
        assertEquals(store.state.sessions[0].id, store.state.activeSessionId)
        store.dispose()
    }

    @Test
    fun `DeleteSession removes session from list`() = runTest {
        val agent = FakeAgent()
        val id = (agent.createSession(title = "To delete") as Either.Right).value

        val store = SessionListStoreFactory(storeFactory = DefaultStoreFactory(), sessionManager = agent).create()
        store.accept(SessionListStore.Intent.LoadSessions)
        advanceUntilIdle()

        store.accept(SessionListStore.Intent.DeleteSession(id))
        advanceUntilIdle()

        assertTrue(store.state.sessions.isEmpty())
        assertTrue(agent.getSession(id = id) is Either.Left)
        store.dispose()
    }

    @Test
    fun `DeleteSession switches active to first remaining if active was deleted`() = runTest {
        val agent = FakeAgent()
        val id1 = (agent.createSession(title = "First") as Either.Right).value
        val id2 = (agent.createSession(title = "Second") as Either.Right).value

        val store = SessionListStoreFactory(storeFactory = DefaultStoreFactory(), sessionManager = agent).create()
        store.accept(SessionListStore.Intent.LoadSessions)
        advanceUntilIdle()

        store.accept(SessionListStore.Intent.SelectSession(id1))
        advanceUntilIdle()
        store.accept(SessionListStore.Intent.DeleteSession(id1))
        advanceUntilIdle()

        assertEquals(1, store.state.sessions.size)
        assertEquals(id2, store.state.activeSessionId)
        store.dispose()
    }

    @Test
    fun `SelectSession sets activeSessionId`() = runTest {
        val agent = FakeAgent()
        val id = (agent.createSession(title = "Test") as Either.Right).value

        val store = SessionListStoreFactory(storeFactory = DefaultStoreFactory(), sessionManager = agent).create()
        store.accept(SessionListStore.Intent.LoadSessions)
        advanceUntilIdle()

        store.accept(SessionListStore.Intent.SelectSession(id))
        advanceUntilIdle()

        assertEquals(id, store.state.activeSessionId)
        store.dispose()
    }
}
