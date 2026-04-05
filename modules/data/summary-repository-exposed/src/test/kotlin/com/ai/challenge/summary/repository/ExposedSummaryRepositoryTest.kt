package com.ai.challenge.summary.repository

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExposedSummaryRepositoryTest {

    private lateinit var repo: ExposedSummaryRepository

    @BeforeTest
    fun setup() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_summary_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        repo = ExposedSummaryRepository(db)
    }

    @Test
    fun `save and retrieve summary by session`() = runTest {
        val sessionId = AgentSessionId("s1")
        val summary = Summary(text = "test summary", fromTurnIndex = 0, toTurnIndex = 5)

        repo.save(sessionId, summary)
        val result = repo.getBySession(sessionId)

        assertEquals(1, result.size)
        assertEquals("test summary", result[0].text)
        assertEquals(0, result[0].fromTurnIndex)
        assertEquals(5, result[0].toTurnIndex)
    }

    @Test
    fun `getBySession returns empty list for unknown session`() = runTest {
        val result = repo.getBySession(AgentSessionId("unknown"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `multiple summaries for same session`() = runTest {
        val sessionId = AgentSessionId("s1")

        repo.save(sessionId, Summary(text = "summary1", fromTurnIndex = 0, toTurnIndex = 3))
        repo.save(sessionId, Summary(text = "summary2", fromTurnIndex = 0, toTurnIndex = 5))

        val result = repo.getBySession(sessionId)
        assertEquals(2, result.size)
    }

    @Test
    fun `summaries from different sessions are isolated`() = runTest {
        repo.save(AgentSessionId("s1"), Summary(text = "s1 summary", fromTurnIndex = 0, toTurnIndex = 3))
        repo.save(AgentSessionId("s2"), Summary(text = "s2 summary", fromTurnIndex = 0, toTurnIndex = 3))

        val s1Result = repo.getBySession(AgentSessionId("s1"))
        assertEquals(1, s1Result.size)
        assertEquals("s1 summary", s1Result[0].text)

        val s2Result = repo.getBySession(AgentSessionId("s2"))
        assertEquals(1, s2Result.size)
        assertEquals("s2 summary", s2Result[0].text)
    }
}
