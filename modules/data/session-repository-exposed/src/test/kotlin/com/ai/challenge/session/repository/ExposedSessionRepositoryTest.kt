package com.ai.challenge.session.repository

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
        repository = ExposedSessionRepository(db)
    }

    @Test
    fun `create and get round-trip`() = runTest {
        val id = repository.create(title = "Test chat")
        val session = repository.get(id)
        assertNotNull(session)
        assertEquals("Test chat", session.title)
        assertEquals(id, session.id)
    }

    @Test
    fun `get returns null for unknown id`() = runTest {
        assertNull(repository.get(AgentSessionId("nonexistent")))
    }

    @Test
    fun `delete removes session and returns true`() = runTest {
        val id = repository.create()
        assertTrue(repository.delete(id))
        assertNull(repository.get(id))
    }

    @Test
    fun `delete returns false for unknown id`() = runTest {
        assertFalse(repository.delete(AgentSessionId("nonexistent")))
    }

    @Test
    fun `list returns all sessions sorted by updatedAt descending`() = runTest {
        val id1 = repository.create(title = "First")
        Thread.sleep(10)
        val id2 = repository.create(title = "Second")

        val sessions = repository.list()
        assertEquals(2, sessions.size)
        assertEquals(id2, sessions[0].id)
        assertEquals(id1, sessions[1].id)
    }

    @Test
    fun `updateTitle changes session title`() = runTest {
        val id = repository.create(title = "Old")
        repository.updateTitle(id, "New")
        assertEquals("New", repository.get(id)?.title)
    }
}
