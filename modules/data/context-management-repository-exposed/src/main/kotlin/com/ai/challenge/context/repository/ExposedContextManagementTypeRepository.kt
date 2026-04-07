package com.ai.challenge.context.repository

import com.ai.challenge.core.context.ContextManagementTypeRepository
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSessionId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

class ExposedContextManagementTypeRepository(
    private val database: Database,
) : ContextManagementTypeRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(ContextManagementTable)
        }
    }

    override suspend fun save(sessionId: AgentSessionId, type: ContextManagementType) {
        transaction(database) {
            ContextManagementTable.upsert {
                it[ContextManagementTable.sessionId] = sessionId.value
                it[ContextManagementTable.type] = type.toStorageString()
            }
        }
    }

    override suspend fun getBySession(sessionId: AgentSessionId): ContextManagementType =
        transaction(database) {
            ContextManagementTable.selectAll()
                .where { ContextManagementTable.sessionId eq sessionId.value }
                .singleOrNull()
                ?.let { it[ContextManagementTable.type].toContextManagementType() }
                ?: ContextManagementType.None
        }

    override suspend fun delete(sessionId: AgentSessionId) {
        transaction(database) {
            ContextManagementTable.deleteWhere { ContextManagementTable.sessionId eq sessionId.value }
        }
    }
}

private fun ContextManagementType.toStorageString(): String = when (this) {
    is ContextManagementType.None -> "none"
    is ContextManagementType.SummarizeOnThreshold -> "summarize_on_threshold"
    is ContextManagementType.SlidingWindow -> "sliding_window"
}

private fun String.toContextManagementType(): ContextManagementType = when (this) {
    "none" -> ContextManagementType.None
    "summarize_on_threshold" -> ContextManagementType.SummarizeOnThreshold
    "sliding_window" -> ContextManagementType.SlidingWindow
    else -> ContextManagementType.None
}
