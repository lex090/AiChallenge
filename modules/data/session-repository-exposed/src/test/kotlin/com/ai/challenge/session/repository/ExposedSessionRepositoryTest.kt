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
        repository = ExposedSessionRepository(database = db)
    }

    @Test
    fun `create and get round-trip`() = runTest {
        val id = repository.create(title = "Test chat")
        val session = repository.get(id = id)
        assertNotNull(actual = session)
        assertEquals(expected = "Test chat", actual = session.title)
        assertEquals(expected = id, actual = session.id)
    }

    @Test
    fun `get returns null for unknown id`() = runTest {
        assertNull(actual = repository.get(id = AgentSessionId(value = "nonexistent")))
    }

    @Test
    fun `delete removes session and returns true`() = runTest {
        val id = repository.create(title = "")
        assertTrue(actual = repository.delete(id = id))
        assertNull(actual = repository.get(id = id))
    }

    @Test
    fun `delete returns false for unknown id`() = runTest {
        assertFalse(actual = repository.delete(id = AgentSessionId(value = "nonexistent")))
    }

    @Test
    fun `list returns all sessions sorted by updatedAt descending`() = runTest {
        val id1 = repository.create(title = "First")
        Thread.sleep(10)
        val id2 = repository.create(title = "Second")

        val sessions = repository.list()
        assertEquals(expected = 2, actual = sessions.size)
        assertEquals(expected = id2, actual = sessions[0].id)
        assertEquals(expected = id1, actual = sessions[1].id)
    }

    @Test
    fun `updateTitle changes session title`() = runTest {
        val id = repository.create(title = "Old")
        repository.updateTitle(id = id, title = "New")
        assertEquals(expected = "New", actual = repository.get(id = id)?.title)
    }
}
