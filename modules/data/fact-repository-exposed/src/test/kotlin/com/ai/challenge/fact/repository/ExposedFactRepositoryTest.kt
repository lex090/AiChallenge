package com.ai.challenge.fact.repository

import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactId
import com.ai.challenge.core.session.AgentSessionId
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
        repo = ExposedFactRepository(database = db)
    }

    @Test
    fun `save and retrieve facts by session`() = runTest {
        val sessionId = AgentSessionId("s1")
        val facts = listOf(
            Fact(id = FactId.generate(), sessionId = sessionId, category = FactCategory.Goal, key = "main_goal", value = "Build a chat bot"),
            Fact(id = FactId.generate(), sessionId = sessionId, category = FactCategory.Constraint, key = "language", value = "Kotlin only"),
        )

        repo.save(facts = facts)
        val result = repo.getBySession(sessionId = sessionId)

        assertEquals(2, result.size)
        assertEquals("main_goal", result.first { it.category == FactCategory.Goal }.key)
        assertEquals("Build a chat bot", result.first { it.category == FactCategory.Goal }.value)
        assertEquals("language", result.first { it.category == FactCategory.Constraint }.key)
        assertEquals("Kotlin only", result.first { it.category == FactCategory.Constraint }.value)
    }

    @Test
    fun `save overwrites all previous facts`() = runTest {
        val sessionId = AgentSessionId("s1")
        val firstFacts = listOf(
            Fact(id = FactId.generate(), sessionId = sessionId, category = FactCategory.Goal, key = "goal", value = "Old goal"),
        )
        repo.save(facts = firstFacts)

        val secondFacts = listOf(
            Fact(id = FactId.generate(), sessionId = sessionId, category = FactCategory.Goal, key = "goal", value = "New goal"),
            Fact(id = FactId.generate(), sessionId = sessionId, category = FactCategory.Decision, key = "framework", value = "Ktor"),
        )
        repo.save(facts = secondFacts)

        val result = repo.getBySession(sessionId = sessionId)
        assertEquals(2, result.size)
        assertEquals("New goal", result.first { it.category == FactCategory.Goal }.value)
    }

    @Test
    fun `deleteBySession removes all facts`() = runTest {
        val sessionId = AgentSessionId("s1")
        val facts = listOf(
            Fact(id = FactId.generate(), sessionId = sessionId, category = FactCategory.Goal, key = "goal", value = "A goal"),
        )
        repo.save(facts = facts)

        repo.deleteBySession(sessionId = sessionId)
        val result = repo.getBySession(sessionId = sessionId)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getBySession returns empty list for unknown session`() = runTest {
        val result = repo.getBySession(sessionId = AgentSessionId("unknown"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `facts from different sessions are isolated`() = runTest {
        val s1 = AgentSessionId("s1")
        val s2 = AgentSessionId("s2")

        repo.save(facts = listOf(
            Fact(id = FactId.generate(), sessionId = s1, category = FactCategory.Goal, key = "goal", value = "S1 goal"),
        ))
        repo.save(facts = listOf(
            Fact(id = FactId.generate(), sessionId = s2, category = FactCategory.Goal, key = "goal", value = "S2 goal"),
        ))

        val s1Result = repo.getBySession(sessionId = s1)
        assertEquals(1, s1Result.size)
        assertEquals("S1 goal", s1Result[0].value)

        val s2Result = repo.getBySession(sessionId = s2)
        assertEquals(1, s2Result.size)
        assertEquals("S2 goal", s2Result[0].value)
    }

    @Test
    fun `save with empty list is a no-op`() = runTest {
        val sessionId = AgentSessionId("s1")
        repo.save(facts = listOf(
            Fact(id = FactId.generate(), sessionId = sessionId, category = FactCategory.Goal, key = "goal", value = "A goal"),
        ))

        repo.save(facts = emptyList())
        val result = repo.getBySession(sessionId = sessionId)

        assertEquals(1, result.size)
        assertEquals("A goal", result[0].value)
    }
}
