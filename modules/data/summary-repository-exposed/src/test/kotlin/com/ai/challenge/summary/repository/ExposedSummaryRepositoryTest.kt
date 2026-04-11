package com.ai.challenge.summary.repository

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryId
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
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
        repo = ExposedSummaryRepository(database = db)
    }

    @Test
    fun `save and retrieve summary by session`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val summary = Summary(id = SummaryId.generate(), text = "test summary", fromTurnIndex = 0, toTurnIndex = 5, createdAt = Clock.System.now())

        repo.save(sessionId = sessionId, summary = summary)
        val result = repo.getBySession(sessionId = sessionId)

        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = "test summary", actual = result[0].text)
        assertEquals(expected = 0, actual = result[0].fromTurnIndex)
        assertEquals(expected = 5, actual = result[0].toTurnIndex)
    }

    @Test
    fun `getBySession returns empty list for unknown session`() = runTest {
        val result = repo.getBySession(sessionId = AgentSessionId(value = "unknown"))
        assertTrue(actual = result.isEmpty())
    }

    @Test
    fun `multiple summaries for same session`() = runTest {
        val sessionId = AgentSessionId(value = "s1")

        repo.save(sessionId = sessionId, summary = Summary(id = SummaryId.generate(), text = "summary1", fromTurnIndex = 0, toTurnIndex = 3, createdAt = Clock.System.now()))
        repo.save(sessionId = sessionId, summary = Summary(id = SummaryId.generate(), text = "summary2", fromTurnIndex = 0, toTurnIndex = 5, createdAt = Clock.System.now()))

        val result = repo.getBySession(sessionId = sessionId)
        assertEquals(expected = 2, actual = result.size)
    }

    @Test
    fun `summaries from different sessions are isolated`() = runTest {
        repo.save(sessionId = AgentSessionId(value = "s1"), summary = Summary(id = SummaryId.generate(), text = "s1 summary", fromTurnIndex = 0, toTurnIndex = 3, createdAt = Clock.System.now()))
        repo.save(sessionId = AgentSessionId(value = "s2"), summary = Summary(id = SummaryId.generate(), text = "s2 summary", fromTurnIndex = 0, toTurnIndex = 3, createdAt = Clock.System.now()))

        val s1Result = repo.getBySession(sessionId = AgentSessionId(value = "s1"))
        assertEquals(expected = 1, actual = s1Result.size)
        assertEquals(expected = "s1 summary", actual = s1Result[0].text)

        val s2Result = repo.getBySession(sessionId = AgentSessionId(value = "s2"))
        assertEquals(expected = 1, actual = s2Result.size)
        assertEquals(expected = "s2 summary", actual = s2Result[0].text)
    }
}
