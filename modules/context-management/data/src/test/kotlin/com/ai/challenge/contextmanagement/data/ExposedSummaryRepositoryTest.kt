package com.ai.challenge.contextmanagement.data

import com.ai.challenge.contextmanagement.model.Summary
import com.ai.challenge.contextmanagement.model.SummaryContent
import com.ai.challenge.contextmanagement.model.TurnIndex
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.vo.CreatedAt
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
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
            url = "jdbc:sqlite:/tmp/test_cm_summary_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(SummariesTable)
        }
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
    }

    @Test
    fun `multiple summaries for same session`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        repo.save(summary = Summary(sessionId = sessionId, content = SummaryContent(value = "s1"), fromTurnIndex = TurnIndex(value = 0), toTurnIndex = TurnIndex(value = 3), createdAt = CreatedAt(value = Clock.System.now())))
        repo.save(summary = Summary(sessionId = sessionId, content = SummaryContent(value = "s2"), fromTurnIndex = TurnIndex(value = 0), toTurnIndex = TurnIndex(value = 5), createdAt = CreatedAt(value = Clock.System.now())))

        assertEquals(expected = 2, actual = repo.getBySession(sessionId = sessionId).size)
    }

    @Test
    fun `deleteBySession removes all summaries`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        repo.save(summary = Summary(sessionId = sessionId, content = SummaryContent(value = "s"), fromTurnIndex = TurnIndex(value = 0), toTurnIndex = TurnIndex(value = 3), createdAt = CreatedAt(value = Clock.System.now())))

        repo.deleteBySession(sessionId = sessionId)

        assertTrue(actual = repo.getBySession(sessionId = sessionId).isEmpty())
    }

    @Test
    fun `deleteSummary removes specific summary`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val now = Clock.System.now()
        repo.save(summary = Summary(sessionId = sessionId, content = SummaryContent(value = "keep"), fromTurnIndex = TurnIndex(value = 0), toTurnIndex = TurnIndex(value = 3), createdAt = CreatedAt(value = now)))
        repo.save(summary = Summary(sessionId = sessionId, content = SummaryContent(value = "remove"), fromTurnIndex = TurnIndex(value = 0), toTurnIndex = TurnIndex(value = 5), createdAt = CreatedAt(value = now)))

        repo.deleteSummary(sessionId = sessionId, fromTurnIndex = 0, toTurnIndex = 5, createdAtMillis = now.toEpochMilliseconds())

        val result = repo.getBySession(sessionId = sessionId)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = "keep", actual = result[0].content.value)
    }
}
