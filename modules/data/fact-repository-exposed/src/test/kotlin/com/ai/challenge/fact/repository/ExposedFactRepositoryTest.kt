package com.ai.challenge.fact.repository

import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
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
        val sessionId = AgentSessionId(value = "s1")
        val facts = listOf(
            Fact(sessionId = sessionId, category = FactCategory.Goal, key = FactKey(value = "main_goal"), value = FactValue(value = "Build a chat bot")),
            Fact(sessionId = sessionId, category = FactCategory.Constraint, key = FactKey(value = "language"), value = FactValue(value = "Kotlin only")),
        )

        repo.save(sessionId = sessionId, facts = facts)
        val result = repo.getBySession(sessionId = sessionId)

        assertEquals(expected = 2, actual = result.size)
        assertEquals(expected = "main_goal", actual = result.first { it.category == FactCategory.Goal }.key.value)
        assertEquals(expected = "Build a chat bot", actual = result.first { it.category == FactCategory.Goal }.value.value)
        assertEquals(expected = "language", actual = result.first { it.category == FactCategory.Constraint }.key.value)
        assertEquals(expected = "Kotlin only", actual = result.first { it.category == FactCategory.Constraint }.value.value)
    }

    @Test
    fun `save overwrites all previous facts`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val firstFacts = listOf(
            Fact(sessionId = sessionId, category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "Old goal")),
        )
        repo.save(sessionId = sessionId, facts = firstFacts)

        val secondFacts = listOf(
            Fact(sessionId = sessionId, category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "New goal")),
            Fact(sessionId = sessionId, category = FactCategory.Decision, key = FactKey(value = "framework"), value = FactValue(value = "Ktor")),
        )
        repo.save(sessionId = sessionId, facts = secondFacts)

        val result = repo.getBySession(sessionId = sessionId)
        assertEquals(expected = 2, actual = result.size)
        assertEquals(expected = "New goal", actual = result.first { it.category == FactCategory.Goal }.value.value)
    }

    @Test
    fun `deleteBySession removes all facts`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val facts = listOf(
            Fact(sessionId = sessionId, category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "A goal")),
        )
        repo.save(sessionId = sessionId, facts = facts)

        repo.deleteBySession(sessionId = sessionId)
        val result = repo.getBySession(sessionId = sessionId)

        assertTrue(actual = result.isEmpty())
    }

    @Test
    fun `getBySession returns empty list for unknown session`() = runTest {
        val result = repo.getBySession(sessionId = AgentSessionId(value = "unknown"))
        assertTrue(actual = result.isEmpty())
    }

    @Test
    fun `facts from different sessions are isolated`() = runTest {
        val s1 = AgentSessionId(value = "s1")
        val s2 = AgentSessionId(value = "s2")

        repo.save(sessionId = s1, facts = listOf(
            Fact(sessionId = s1, category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "S1 goal")),
        ))
        repo.save(sessionId = s2, facts = listOf(
            Fact(sessionId = s2, category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "S2 goal")),
        ))

        val s1Result = repo.getBySession(sessionId = s1)
        assertEquals(expected = 1, actual = s1Result.size)
        assertEquals(expected = "S1 goal", actual = s1Result[0].value.value)

        val s2Result = repo.getBySession(sessionId = s2)
        assertEquals(expected = 1, actual = s2Result.size)
        assertEquals(expected = "S2 goal", actual = s2Result[0].value.value)
    }

    @Test
    fun `save with empty list clears facts for session`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        repo.save(sessionId = sessionId, facts = listOf(
            Fact(sessionId = sessionId, category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "A goal")),
        ))

        repo.save(sessionId = sessionId, facts = emptyList())
        val result = repo.getBySession(sessionId = sessionId)

        assertTrue(actual = result.isEmpty())
    }
}
