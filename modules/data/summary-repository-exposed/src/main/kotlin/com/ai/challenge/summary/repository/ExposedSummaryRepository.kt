package com.ai.challenge.summary.repository

import com.ai.challenge.core.context.model.SummaryContent
import com.ai.challenge.core.context.model.TurnIndex
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
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

    private fun ResultRow.toSummary() = Summary(
        sessionId = AgentSessionId(value = this[SummariesTable.sessionId]),
        content = SummaryContent(value = this[SummariesTable.text]),
        fromTurnIndex = TurnIndex(value = this[SummariesTable.fromTurnIndex]),
        toTurnIndex = TurnIndex(value = this[SummariesTable.toTurnIndex]),
        createdAt = CreatedAt(value = Instant.fromEpochMilliseconds(this[SummariesTable.createdAt])),
    )
}
