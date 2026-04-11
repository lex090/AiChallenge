package com.ai.challenge.session.repository

import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class ExposedSessionRepositoryTest {

    private lateinit var repository: ExposedSessionRepository

    @BeforeTest
    fun setUp() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_session_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
            setupConnection = { conn ->
                conn.createStatement().execute("PRAGMA foreign_keys = ON")
            },
        )
        repository = ExposedSessionRepository(database = db)
    }

    private fun createSession(title: String): AgentSession {
        val now = Clock.System.now()
        return AgentSession(
            id = AgentSessionId.generate(),
            title = title,
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test
    fun `save and get round-trip`() = runTest {
        val session = createSession(title = "Test chat")
        val id = repository.save(session = session)
        val loaded = repository.get(id = id)
        assertNotNull(actual = loaded)
        assertEquals(expected = "Test chat", actual = loaded.title)
        assertEquals(expected = id, actual = loaded.id)
    }

    @Test
    fun `get returns null for unknown id`() = runTest {
        assertNull(actual = repository.get(id = AgentSessionId(value = "nonexistent")))
    }

    @Test
    fun `delete removes session and returns true`() = runTest {
        val session = createSession(title = "")
        val id = repository.save(session = session)
        assertTrue(actual = repository.delete(id = id))
        assertNull(actual = repository.get(id = id))
    }

    @Test
    fun `delete returns false for unknown id`() = runTest {
        assertFalse(actual = repository.delete(id = AgentSessionId(value = "nonexistent")))
    }

    @Test
    fun `list returns all sessions sorted by updatedAt descending`() = runTest {
        val session1 = createSession(title = "First")
        val id1 = repository.save(session = session1)
        Thread.sleep(10)
        val session2 = createSession(title = "Second")
        val id2 = repository.save(session = session2)

        val sessions = repository.list()
        assertEquals(expected = 2, actual = sessions.size)
        assertEquals(expected = id2, actual = sessions[0].id)
        assertEquals(expected = id1, actual = sessions[1].id)
    }

    @Test
    fun `update changes session title`() = runTest {
        val session = createSession(title = "Old")
        val id = repository.save(session = session)
        val loaded = repository.get(id = id)!!
        repository.update(session = loaded.copy(title = "New", updatedAt = Clock.System.now()))
        assertEquals(expected = "New", actual = repository.get(id = id)?.title)
    }
}
