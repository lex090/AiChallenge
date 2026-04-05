package com.ai.challenge.session

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ExposedSessionManagerTest {

    private lateinit var db: Database
    private lateinit var manager: ExposedSessionManager

    @BeforeTest
    fun setUp() {
        db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_session_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
            setupConnection = { conn ->
                conn.createStatement().execute("PRAGMA foreign_keys = ON")
            },
        )
        manager = ExposedSessionManager(db)
    }

    @Test
    fun `createSession and getSession round-trip`() {
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
    fun `deleteSession cascades to turns`() {
        val id = manager.createSession()
        manager.appendTurn(id, Turn(userMessage = "hi", agentResponse = "hello"))
        manager.deleteSession(id)
        assertTrue(manager.getHistory(id).isEmpty())
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
    fun `listSessions returns sessions without history loaded`() {
        val id = manager.createSession()
        manager.appendTurn(id, Turn(userMessage = "hi", agentResponse = "hello"))

        val sessions = manager.listSessions()
        assertTrue(sessions[0].history.isEmpty())
    }

    @Test
    fun `appendTurn and getHistory round-trip`() {
        val id = manager.createSession()
        manager.appendTurn(id, Turn(userMessage = "hi", agentResponse = "hello"))
        manager.appendTurn(id, Turn(userMessage = "how", agentResponse = "fine"))

        val history = manager.getHistory(id)
        assertEquals(2, history.size)
        assertEquals("hi", history[0].userMessage)
        assertEquals("hello", history[0].agentResponse)
        assertEquals("how", history[1].userMessage)
        assertEquals("fine", history[1].agentResponse)
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

    @Test
    fun `getSession loads full history`() {
        val id = manager.createSession()
        manager.appendTurn(id, Turn(userMessage = "hi", agentResponse = "hello"))

        val session = manager.getSession(id)
        assertNotNull(session)
        assertEquals(1, session.history.size)
        assertEquals("hi", session.history[0].userMessage)
    }

}
