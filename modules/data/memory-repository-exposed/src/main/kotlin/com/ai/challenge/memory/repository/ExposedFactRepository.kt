package com.ai.challenge.memory.repository

import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.session.AgentSessionId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedFactRepository(private val database: Database) : FactRepository {

    override suspend fun save(sessionId: AgentSessionId, facts: List<Fact>) {
        transaction(database) {
            FactsTable.deleteWhere { FactsTable.sessionId eq sessionId.value }
            FactsTable.batchInsert(facts) { fact ->
                this[FactsTable.sessionId] = fact.sessionId.value
                this[FactsTable.category] = fact.category.toStorageString()
                this[FactsTable.key] = fact.key.value
                this[FactsTable.value] = fact.value.value
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
        sessionId = AgentSessionId(value = this[FactsTable.sessionId]),
        category = this[FactsTable.category].toFactCategory(),
        key = FactKey(value = this[FactsTable.key]),
        value = FactValue(value = this[FactsTable.value]),
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
