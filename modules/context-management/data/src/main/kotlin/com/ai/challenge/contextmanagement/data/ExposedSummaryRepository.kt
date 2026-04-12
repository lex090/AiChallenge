package com.ai.challenge.contextmanagement.data

import com.ai.challenge.contextmanagement.model.Summary
import com.ai.challenge.contextmanagement.model.SummaryContent
import com.ai.challenge.contextmanagement.model.TurnIndex
import com.ai.challenge.contextmanagement.repository.SummaryRepository
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.vo.CreatedAt
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Instant

/**
 * Exposed-backed implementation of [SummaryRepository].
 *
 * Persists [Summary] value objects in the SQLite "summaries" table.
 * Append-only: [save] inserts a new summary without modifying existing ones.
 * Also provides [deleteSummary] for removing a specific summary by its composite key.
 */
class ExposedSummaryRepository(private val database: Database) : SummaryRepository {

    override suspend fun save(summary: Summary) {
        transaction(database) {
            SummariesTable.insert {
                it[sessionId] = summary.sessionId.value
                it[text] = summary.content.value
                it[fromTurnIndex] = summary.fromTurnIndex.value
                it[toTurnIndex] = summary.toTurnIndex.value
                it[createdAt] = summary.createdAt.value.toEpochMilliseconds()
            }
        }
    }

    override suspend fun getBySession(sessionId: AgentSessionId): List<Summary> = transaction(database) {
        SummariesTable.selectAll()
            .where { SummariesTable.sessionId eq sessionId.value }
            .map { it.toSummary() }
    }

    override suspend fun deleteBySession(sessionId: AgentSessionId) {
        transaction(database) {
            SummariesTable.deleteWhere { SummariesTable.sessionId eq sessionId.value }
        }
    }

    /**
     * Deletes a specific summary identified by its composite key:
     * session ID, turn range, and creation timestamp.
     */
    fun deleteSummary(sessionId: AgentSessionId, fromTurnIndex: Int, toTurnIndex: Int, createdAtMillis: Long) {
        transaction(database) {
            SummariesTable.deleteWhere {
                (SummariesTable.sessionId eq sessionId.value) and
                    (SummariesTable.fromTurnIndex eq fromTurnIndex) and
                    (SummariesTable.toTurnIndex eq toTurnIndex) and
                    (SummariesTable.createdAt eq createdAtMillis)
            }
        }
    }

    private fun ResultRow.toSummary() = Summary(
        sessionId = AgentSessionId(value = this[SummariesTable.sessionId]),
        content = SummaryContent(value = this[SummariesTable.text]),
        fromTurnIndex = TurnIndex(value = this[SummariesTable.fromTurnIndex]),
        toTurnIndex = TurnIndex(value = this[SummariesTable.toTurnIndex]),
        createdAt = CreatedAt(value = Instant.fromEpochMilliseconds(this[SummariesTable.createdAt])),
    )
}
