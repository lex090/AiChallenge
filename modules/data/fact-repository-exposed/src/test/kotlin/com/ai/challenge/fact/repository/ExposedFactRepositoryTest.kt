package com.ai.challenge.fact.repository

import com.ai.challenge.core.Fact
import com.ai.challenge.core.SessionId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExposedFactRepositoryTest {

    private lateinit var repo: ExposedFactRepository

    @BeforeTest
    fun setup() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_fact_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        repo = ExposedFactRepository(db)
    }

    @Test
    fun `save and retrieve facts by session`() = runTest {
        val sessionId = SessionId("s1")
        val facts = listOf(
            Fact(content = "User likes Kotlin"),
            Fact(content = "Project uses Gradle"),
        )

        repo.save(sessionId, facts)
        val result = repo.getBySession(sessionId)

        assertEquals(2, result.size)
        assertEquals("User likes Kotlin", result[0].content)
        assertEquals("Project uses Gradle", result[1].content)
    }

    @Test
    fun `save replaces existing facts`() = runTest {
        val sessionId = SessionId("s1")
        repo.save(sessionId, listOf(Fact(content = "old fact")))
        repo.save(sessionId, listOf(Fact(content = "new fact")))

        val result = repo.getBySession(sessionId)
        assertEquals(1, result.size)
        assertEquals("new fact", result[0].content)
    }

    @Test
    fun `getBySession returns empty for unknown session`() = runTest {
        val result = repo.getBySession(SessionId("unknown"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `facts from different sessions are isolated`() = runTest {
        repo.save(SessionId("s1"), listOf(Fact(content = "s1 fact")))
        repo.save(SessionId("s2"), listOf(Fact(content = "s2 fact")))

        assertEquals("s1 fact", repo.getBySession(SessionId("s1"))[0].content)
        assertEquals("s2 fact", repo.getBySession(SessionId("s2"))[0].content)
    }
}
