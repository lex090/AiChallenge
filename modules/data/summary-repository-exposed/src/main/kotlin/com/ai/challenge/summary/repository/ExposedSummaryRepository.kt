package com.ai.challenge.summary.repository

import com.ai.challenge.core.session.SessionId
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryId
import com.ai.challenge.core.summary.SummaryRepository
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Instant

class ExposedSummaryRepository(private val database: Database) : SummaryRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(SummariesTable)
        }
    }

    override suspend fun save(sessionId: SessionId, summary: Summary) {
        transaction(database) {
            SummariesTable.insert {
                it[id] = summary.id.value
                it[SummariesTable.sessionId] = sessionId.value
                it[text] = summary.text
                it[fromTurnIndex] = summary.fromTurnIndex
                it[toTurnIndex] = summary.toTurnIndex
                it[createdAt] = summary.createdAt.toEpochMilliseconds()
            }
        }
    }

    override suspend fun getBySession(sessionId: SessionId): List<Summary> = transaction(database) {
        SummariesTable.selectAll()
            .where { SummariesTable.sessionId eq sessionId.value }
            .map { it.toSummary() }
    }

    private fun ResultRow.toSummary() = Summary(
        id = SummaryId(this[SummariesTable.id]),
        text = this[SummariesTable.text],
        fromTurnIndex = this[SummariesTable.fromTurnIndex],
        toTurnIndex = this[SummariesTable.toTurnIndex],
        createdAt = Instant.fromEpochMilliseconds(this[SummariesTable.createdAt]),
    )
}
