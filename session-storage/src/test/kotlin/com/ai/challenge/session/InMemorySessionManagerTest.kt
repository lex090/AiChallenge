package com.ai.challenge.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class InMemorySessionManagerTest {

    private val manager = InMemorySessionManager()

    @Test
    fun `createSession returns unique session id`() {
        val id1 = manager.createSession()
        val id2 = manager.createSession()
        assertTrue(id1 != id2)
    }

    @Test
    fun `getSession returns created session`() {
        val id = manager.createSession(title = "Test chat")
        val session = manager.getSession(id)
        assertNotNull(session)
        assertEquals("Test chat", session.title)
        assertEquals(id, session.id)
        assertTrue(session.history.isEmpty())
    }

    @Test
    fun `getSession returns null for unknown id`() {
        assertNull(manager.getSession(SessionId("nonexistent")))
    }

    @Test
    fun `deleteSession removes session and returns true`() {
        val id = manager.createSession()
        assertTrue(manager.deleteSession(id))
        assertNull(manager.getSession(id))
    }

    @Test
    fun `deleteSession returns false for unknown id`() {
        assertFalse(manager.deleteSession(SessionId("nonexistent")))
    }

    @Test
    fun `listSessions returns all sessions sorted by updatedAt descending`() {
        val id1 = manager.createSession(title = "First")
        val id2 = manager.createSession(title = "Second")
        manager.appendTurn(id1, Turn(userMessage = "hi", agentResponse = "hello"))

        val sessions = manager.listSessions()
        assertEquals(2, sessions.size)
        assertEquals(id1, sessions[0].id)
        assertEquals(id2, sessions[1].id)
    }

    @Test
    fun `appendTurn adds turn to session history`() {
        val id = manager.createSession()
        val turn = Turn(userMessage = "hi", agentResponse = "hello")
        manager.appendTurn(id, turn)

        val history = manager.getHistory(id)
        assertEquals(1, history.size)
        assertEquals("hi", history[0].userMessage)
        assertEquals("hello", history[0].agentResponse)
    }

    @Test
    fun `getHistory with limit returns last N turns`() {
        val id = manager.createSession()
        manager.appendTurn(id, Turn(userMessage = "1", agentResponse = "a"))
        manager.appendTurn(id, Turn(userMessage = "2", agentResponse = "b"))
        manager.appendTurn(id, Turn(userMessage = "3", agentResponse = "c"))

        val history = manager.getHistory(id, limit = 2)
        assertEquals(2, history.size)
        assertEquals("2", history[0].userMessage)
        assertEquals("3", history[1].userMessage)
    }

    @Test
    fun `getHistory returns empty list for unknown session`() {
        assertTrue(manager.getHistory(SessionId("nonexistent")).isEmpty())
    }

    @Test
    fun `updateTitle changes session title`() {
        val id = manager.createSession(title = "Old")
        manager.updateTitle(id, "New")
        assertEquals("New", manager.getSession(id)?.title)
    }
}
