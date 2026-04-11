package com.ai.challenge.turn.repository

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
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
        val sessionId = AgentSessionId.generate()
        val turn = Turn(id = TurnId.generate(), userMessage = "hi", agentResponse = "hello", timestamp = Clock.System.now())
        repository.append(sessionId, turn)

        val history = repository.getBySession(sessionId = sessionId, limit = null)
        assertEquals(1, history.size)
        assertEquals("hi", history[0].userMessage)
        assertEquals("hello", history[0].agentResponse)
    }

    @Test
    fun `getBySession returns empty list for unknown session`() = runTest {
        assertTrue(repository.getBySession(sessionId = AgentSessionId("nonexistent"), limit = null).isEmpty())
    }

    @Test
    fun `getBySession with limit returns last N turns`() = runTest {
        val sessionId = AgentSessionId.generate()
        repository.append(sessionId, Turn(id = TurnId.generate(), userMessage = "1", agentResponse = "a", timestamp = Clock.System.now()))
        repository.append(sessionId, Turn(id = TurnId.generate(), userMessage = "2", agentResponse = "b", timestamp = Clock.System.now()))
        repository.append(sessionId, Turn(id = TurnId.generate(), userMessage = "3", agentResponse = "c", timestamp = Clock.System.now()))

        val history = repository.getBySession(sessionId = sessionId, limit = 2)
        assertEquals(2, history.size)
        assertEquals("2", history[0].userMessage)
        assertEquals("3", history[1].userMessage)
    }

    @Test
    fun `get returns turn by id`() = runTest {
        val sessionId = AgentSessionId.generate()
        val turn = Turn(id = TurnId.generate(), userMessage = "hi", agentResponse = "hello", timestamp = Clock.System.now())
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
        val session1 = AgentSessionId.generate()
        val session2 = AgentSessionId.generate()
        repository.append(session1, Turn(id = TurnId.generate(), userMessage = "a", agentResponse = "b", timestamp = Clock.System.now()))
        repository.append(session2, Turn(id = TurnId.generate(), userMessage = "c", agentResponse = "d", timestamp = Clock.System.now()))

        val result = repository.getBySession(sessionId = session1, limit = null)
        assertEquals(1, result.size)
        assertEquals("a", result[0].userMessage)
    }
}
