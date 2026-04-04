package com.ai.challenge.ui.sessionlist.store

import com.ai.challenge.session.InMemorySessionManager
import com.ai.challenge.session.Turn
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
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty session list`() {
        val sessionManager = InMemorySessionManager()
        val store = SessionListStoreFactory(DefaultStoreFactory(), sessionManager).create()

        assertTrue(store.state.sessions.isEmpty())
        assertNull(store.state.activeSessionId)

        store.dispose()
    }

    @Test
    fun `LoadSessions populates session list`() = runTest {
        val sessionManager = InMemorySessionManager()
        val id1 = sessionManager.createSession(title = "Chat 1")
        val id2 = sessionManager.createSession(title = "Chat 2")

        val store = SessionListStoreFactory(DefaultStoreFactory(), sessionManager).create()
        store.accept(SessionListStore.Intent.LoadSessions)
        advanceUntilIdle()

        assertEquals(2, store.state.sessions.size)

        store.dispose()
    }

    @Test
    fun `CreateSession creates new session and makes it active`() = runTest {
        val sessionManager = InMemorySessionManager()
        val store = SessionListStoreFactory(DefaultStoreFactory(), sessionManager).create()

        store.accept(SessionListStore.Intent.CreateSession)
        advanceUntilIdle()

        assertEquals(1, store.state.sessions.size)
        assertNotNull(store.state.activeSessionId)
        assertEquals(store.state.sessions[0].id, store.state.activeSessionId)

        store.dispose()
    }

    @Test
    fun `DeleteSession removes session from list`() = runTest {
        val sessionManager = InMemorySessionManager()
        val id = sessionManager.createSession(title = "To delete")

        val store = SessionListStoreFactory(DefaultStoreFactory(), sessionManager).create()
        store.accept(SessionListStore.Intent.LoadSessions)
        advanceUntilIdle()

        store.accept(SessionListStore.Intent.DeleteSession(id))
        advanceUntilIdle()

        assertTrue(store.state.sessions.isEmpty())
        assertNull(sessionManager.getSession(id))

        store.dispose()
    }

    @Test
    fun `DeleteSession switches active to first remaining if active was deleted`() = runTest {
        val sessionManager = InMemorySessionManager()
        val id1 = sessionManager.createSession(title = "First")
        val id2 = sessionManager.createSession(title = "Second")

        val store = SessionListStoreFactory(DefaultStoreFactory(), sessionManager).create()
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
        val sessionManager = InMemorySessionManager()
        val id = sessionManager.createSession(title = "Test")

        val store = SessionListStoreFactory(DefaultStoreFactory(), sessionManager).create()
        store.accept(SessionListStore.Intent.LoadSessions)
        advanceUntilIdle()

        store.accept(SessionListStore.Intent.SelectSession(id))
        advanceUntilIdle()

        assertEquals(id, store.state.activeSessionId)

        store.dispose()
    }
}
