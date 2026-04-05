package com.ai.challenge.fact.repository

import com.ai.challenge.core.Fact
import com.ai.challenge.core.FactRepository
import com.ai.challenge.core.SessionId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Instant

class ExposedFactRepository(private val database: Database) : FactRepository {

    init {
        transaction(database) {
            SchemaUtils.create(FactsTable)
        }
    }

    override suspend fun getBySession(sessionId: SessionId): List<Fact> = transaction(database) {
        FactsTable.selectAll()
            .where { FactsTable.sessionId eq sessionId.value }
            .map { it.toFact() }
    }

    override suspend fun save(sessionId: SessionId, facts: List<Fact>) {
        transaction(database) {
            FactsTable.deleteWhere { FactsTable.sessionId eq sessionId.value }
            for (fact in facts) {
                FactsTable.insert {
                    it[id] = fact.id
                    it[FactsTable.sessionId] = sessionId.value
                    it[content] = fact.content
                    it[createdAt] = fact.createdAt.toEpochMilliseconds()
                }
            }
        }
    }

    private fun ResultRow.toFact() = Fact(
        id = this[FactsTable.id],
        content = this[FactsTable.content],
        createdAt = Instant.fromEpochMilliseconds(this[FactsTable.createdAt]),
    )
}
