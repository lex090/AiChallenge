package com.ai.challenge.turn.repository

import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn
import com.ai.challenge.core.TurnId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedTurnRepositoryTest {

    private lateinit var repository: ExposedTurnRepository

    @BeforeTest
    fun setUp() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_turn_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        repository = ExposedTurnRepository(db)
    }

    @Test
    fun `append and getBySession round-trip`() = runTest {
        val sessionId = SessionId.generate()
        val turn = Turn(userMessage = "hi", agentResponse = "hello")
        repository.append(sessionId, turn)

        val history = repository.getBySession(sessionId)
        assertEquals(1, history.size)
        assertEquals("hi", history[0].userMessage)
        assertEquals("hello", history[0].agentResponse)
    }

    @Test
    fun `getBySession returns empty list for unknown session`() = runTest {
        assertTrue(repository.getBySession(SessionId("nonexistent")).isEmpty())
    }

    @Test
    fun `getBySession with limit returns last N turns`() = runTest {
        val sessionId = SessionId.generate()
        repository.append(sessionId, Turn(userMessage = "1", agentResponse = "a"))
        repository.append(sessionId, Turn(userMessage = "2", agentResponse = "b"))
        repository.append(sessionId, Turn(userMessage = "3", agentResponse = "c"))

        val history = repository.getBySession(sessionId, limit = 2)
        assertEquals(2, history.size)
        assertEquals("2", history[0].userMessage)
        assertEquals("3", history[1].userMessage)
    }

    @Test
    fun `get returns turn by id`() = runTest {
        val sessionId = SessionId.generate()
        val turn = Turn(userMessage = "hi", agentResponse = "hello")
        val turnId = repository.append(sessionId, turn)

        val result = repository.get(turnId)
        assertNotNull(result)
        assertEquals("hi", result.userMessage)
    }

    @Test
    fun `get returns null for unknown turnId`() = runTest {
        assertNull(repository.get(TurnId("nonexistent")))
    }

    @Test
    fun `getBySession does not include turns from other sessions`() = runTest {
        val session1 = SessionId.generate()
        val session2 = SessionId.generate()
        repository.append(session1, Turn(userMessage = "a", agentResponse = "b"))
        repository.append(session2, Turn(userMessage = "c", agentResponse = "d"))

        val result = repository.getBySession(session1)
        assertEquals(1, result.size)
        assertEquals("a", result[0].userMessage)
    }
}
