package com.ai.challenge.ui.sessionlist.store

import arrow.core.Either
import com.ai.challenge.conversation.model.SessionTitle
import com.ai.challenge.ui.chat.store.FakeServices
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
        val fake = FakeServices()
        val store = SessionListStoreFactory(storeFactory = DefaultStoreFactory(), sessionService = fake).create()
        assertTrue(store.state.sessions.isEmpty())
        assertNull(store.state.activeSessionId)
        store.dispose()
    }

    @Test
    fun `LoadSessions populates session list when filtering free sessions`() = runTest {
        val fake = FakeServices()
        (fake.create(title = SessionTitle(value = "Chat 1"), projectId = null) as Either.Right).value
        (fake.create(title = SessionTitle(value = "Chat 2"), projectId = null) as Either.Right).value

        val store = SessionListStoreFactory(storeFactory = DefaultStoreFactory(), sessionService = fake).create()
        store.accept(SessionListStore.Intent.FilterByProject(projectId = null))
        advanceUntilIdle()

        assertEquals(2, store.state.sessions.size)
        store.dispose()
    }

    @Test
    fun `CreateSession creates new session and makes it active`() = runTest {
        val fake = FakeServices()
        val store = SessionListStoreFactory(storeFactory = DefaultStoreFactory(), sessionService = fake).create()

        store.accept(SessionListStore.Intent.CreateSession)
        advanceUntilIdle()

        assertEquals(1, store.state.sessions.size)
        assertNotNull(store.state.activeSessionId)
        assertEquals(store.state.sessions[0].id, store.state.activeSessionId)
        store.dispose()
    }

    @Test
    fun `DeleteSession removes session from list`() = runTest {
        val fake = FakeServices()
        val session = (fake.create(title = SessionTitle(value = "To delete"), projectId = null) as Either.Right).value
        val id = session.id

        val store = SessionListStoreFactory(storeFactory = DefaultStoreFactory(), sessionService = fake).create()
        store.accept(SessionListStore.Intent.FilterByProject(projectId = null))
        advanceUntilIdle()

        store.accept(SessionListStore.Intent.DeleteSession(id))
        advanceUntilIdle()

        assertTrue(store.state.sessions.isEmpty())
        assertTrue(fake.get(id = id) is Either.Left)
        store.dispose()
    }

    @Test
    fun `DeleteSession switches active to first remaining if active was deleted`() = runTest {
        val fake = FakeServices()
        val session1 = (fake.create(title = SessionTitle(value = "First"), projectId = null) as Either.Right).value
        val id1 = session1.id
        val session2 = (fake.create(title = SessionTitle(value = "Second"), projectId = null) as Either.Right).value
        val id2 = session2.id

        val store = SessionListStoreFactory(storeFactory = DefaultStoreFactory(), sessionService = fake).create()
        store.accept(SessionListStore.Intent.FilterByProject(projectId = null))
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
        val fake = FakeServices()
        val session = (fake.create(title = SessionTitle(value = "Test"), projectId = null) as Either.Right).value
        val id = session.id

        val store = SessionListStoreFactory(storeFactory = DefaultStoreFactory(), sessionService = fake).create()
        store.accept(SessionListStore.Intent.FilterByProject(projectId = null))
        advanceUntilIdle()

        store.accept(SessionListStore.Intent.SelectSession(id))
        advanceUntilIdle()

        assertEquals(id, store.state.activeSessionId)
        store.dispose()
    }
}
