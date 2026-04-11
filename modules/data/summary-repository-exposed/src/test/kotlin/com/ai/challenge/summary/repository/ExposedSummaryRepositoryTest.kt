package com.ai.challenge.summary.repository

import com.ai.challenge.core.context.model.SummaryContent
import com.ai.challenge.core.context.model.TurnIndex
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.summary.Summary
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

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
        val summary = Summary(
            sessionId = sessionId,
            content = SummaryContent(value = "test summary"),
            fromTurnIndex = TurnIndex(value = 0),
            toTurnIndex = TurnIndex(value = 5),
            createdAt = CreatedAt(value = Clock.System.now()),
        )

        repo.save(summary = summary)
        val result = repo.getBySession(sessionId = sessionId)

        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = "test summary", actual = result[0].content.value)
        assertEquals(expected = 0, actual = result[0].fromTurnIndex.value)
        assertEquals(expected = 5, actual = result[0].toTurnIndex.value)
    }

    @Test
    fun `getBySession returns empty list for unknown session`() = runTest {
        val result = repo.getBySession(sessionId = AgentSessionId(value = "unknown"))
        assertTrue(actual = result.isEmpty())
    }

    @Test
    fun `multiple summaries for same session`() = runTest {
        val sessionId = AgentSessionId(value = "s1")

        repo.save(summary = Summary(sessionId = sessionId, content = SummaryContent(value = "summary1"), fromTurnIndex = TurnIndex(value = 0), toTurnIndex = TurnIndex(value = 3), createdAt = CreatedAt(value = Clock.System.now())))
        repo.save(summary = Summary(sessionId = sessionId, content = SummaryContent(value = "summary2"), fromTurnIndex = TurnIndex(value = 0), toTurnIndex = TurnIndex(value = 5), createdAt = CreatedAt(value = Clock.System.now())))

        val result = repo.getBySession(sessionId = sessionId)
        assertEquals(expected = 2, actual = result.size)
    }

    @Test
    fun `summaries from different sessions are isolated`() = runTest {
        repo.save(summary = Summary(sessionId = AgentSessionId(value = "s1"), content = SummaryContent(value = "s1 summary"), fromTurnIndex = TurnIndex(value = 0), toTurnIndex = TurnIndex(value = 3), createdAt = CreatedAt(value = Clock.System.now())))
        repo.save(summary = Summary(sessionId = AgentSessionId(value = "s2"), content = SummaryContent(value = "s2 summary"), fromTurnIndex = TurnIndex(value = 0), toTurnIndex = TurnIndex(value = 3), createdAt = CreatedAt(value = Clock.System.now())))

        val s1Result = repo.getBySession(sessionId = AgentSessionId(value = "s1"))
        assertEquals(expected = 1, actual = s1Result.size)
        assertEquals(expected = "s1 summary", actual = s1Result[0].content.value)

        val s2Result = repo.getBySession(sessionId = AgentSessionId(value = "s2"))
        assertEquals(expected = 1, actual = s2Result.size)
        assertEquals(expected = "s2 summary", actual = s2Result[0].content.value)
    }

    @Test
    fun `deleteBySession removes all summaries`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        repo.save(summary = Summary(sessionId = sessionId, content = SummaryContent(value = "summary"), fromTurnIndex = TurnIndex(value = 0), toTurnIndex = TurnIndex(value = 3), createdAt = CreatedAt(value = Clock.System.now())))

        repo.deleteBySession(sessionId = sessionId)
        val result = repo.getBySession(sessionId = sessionId)

        assertTrue(actual = result.isEmpty())
    }
}
