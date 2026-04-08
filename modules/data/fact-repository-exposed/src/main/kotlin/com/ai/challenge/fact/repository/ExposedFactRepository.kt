package com.ai.challenge.fact.repository

import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactId
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.session.AgentSessionId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedFactRepository(private val database: Database) : FactRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(FactsTable)
        }
    }

    override suspend fun save(sessionId: AgentSessionId, facts: List<Fact>) {
        transaction(database) {
            FactsTable.deleteWhere { FactsTable.sessionId eq sessionId.value }
            FactsTable.batchInsert(facts) { fact ->
                this[FactsTable.id] = fact.id.value
                this[FactsTable.sessionId] = sessionId.value
                this[FactsTable.category] = fact.category.toStorageString()
                this[FactsTable.key] = fact.key
                this[FactsTable.value] = fact.value
            }
        }
    }

    override suspend fun getBySession(sessionId: AgentSessionId): List<Fact> =
        transaction(database) {
            FactsTable.selectAll()
                .where { FactsTable.sessionId eq sessionId.value }
                .map { it.toFact() }
        }

    override suspend fun deleteBySession(sessionId: AgentSessionId) {
        transaction(database) {
            FactsTable.deleteWhere { FactsTable.sessionId eq sessionId.value }
        }
    }

    private fun ResultRow.toFact() = Fact(
        id = FactId(value = this[FactsTable.id]),
        category = this[FactsTable.category].toFactCategory(),
        key = this[FactsTable.key],
        value = this[FactsTable.value],
    )
}

private fun FactCategory.toStorageString(): String = when (this) {
    FactCategory.Goal -> "goal"
    FactCategory.Constraint -> "constraint"
    FactCategory.Preference -> "preference"
    FactCategory.Decision -> "decision"
    FactCategory.Agreement -> "agreement"
}

private fun String.toFactCategory(): FactCategory = when (this) {
    "goal" -> FactCategory.Goal
    "constraint" -> FactCategory.Constraint
    "preference" -> FactCategory.Preference
    "decision" -> FactCategory.Decision
    "agreement" -> FactCategory.Agreement
    else -> error("Unknown fact category: $this")
}
