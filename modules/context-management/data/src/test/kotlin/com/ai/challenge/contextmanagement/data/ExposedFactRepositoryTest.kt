package com.ai.challenge.contextmanagement.data

import com.ai.challenge.contextmanagement.model.Fact
import com.ai.challenge.contextmanagement.model.FactCategory
import com.ai.challenge.contextmanagement.model.FactKey
import com.ai.challenge.contextmanagement.model.FactValue
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExposedFactRepositoryTest {

    private lateinit var repo: ExposedFactRepository

    @BeforeTest
    fun setup() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_cm_fact_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(FactsTable)
        }
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
    }

    @Test
    fun `save overwrites all previous facts`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        repo.save(sessionId = sessionId, facts = listOf(
            Fact(sessionId = sessionId, category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "Old goal")),
        ))

        repo.save(sessionId = sessionId, facts = listOf(
            Fact(sessionId = sessionId, category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "New goal")),
        ))

        val result = repo.getBySession(sessionId = sessionId)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = "New goal", actual = result[0].value.value)
    }

    @Test
    fun `deleteBySession removes all facts`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        repo.save(sessionId = sessionId, facts = listOf(
            Fact(sessionId = sessionId, category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "A goal")),
        ))

        repo.deleteBySession(sessionId = sessionId)

        assertTrue(actual = repo.getBySession(sessionId = sessionId).isEmpty())
    }

    @Test
    fun `facts from different sessions are isolated`() = runTest {
        val s1 = AgentSessionId(value = "s1")
        val s2 = AgentSessionId(value = "s2")
        repo.save(sessionId = s1, facts = listOf(Fact(sessionId = s1, category = FactCategory.Goal, key = FactKey(value = "g"), value = FactValue(value = "S1"))))
        repo.save(sessionId = s2, facts = listOf(Fact(sessionId = s2, category = FactCategory.Goal, key = FactKey(value = "g"), value = FactValue(value = "S2"))))

        assertEquals(expected = "S1", actual = repo.getBySession(sessionId = s1)[0].value.value)
        assertEquals(expected = "S2", actual = repo.getBySession(sessionId = s2)[0].value.value)
    }
}
