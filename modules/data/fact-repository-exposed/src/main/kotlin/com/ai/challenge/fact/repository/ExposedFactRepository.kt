package com.ai.challenge.fact.repository

import com.ai.challenge.core.Fact
import com.ai.challenge.core.FactRepository
import com.ai.challenge.core.SessionId
import org.jetbrains.exposed.sql.Database
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

    override suspend fun save(sessionId: SessionId, facts: List<Fact>) {
        transaction(database) {
            FactsTable.deleteWhere { FactsTable.sessionId eq sessionId.value }
            for (fact in facts) {
                FactsTable.insert {
                    it[FactsTable.sessionId] = sessionId.value
                    it[key] = fact.key
                    it[value] = fact.value
                    it[updatedAt] = fact.updatedAt.toEpochMilliseconds()
                }
            }
        }
    }

    override suspend fun getBySession(sessionId: SessionId): List<Fact> = transaction(database) {
        FactsTable.selectAll()
            .where { FactsTable.sessionId eq sessionId.value }
            .map {
                Fact(
                    key = it[FactsTable.key],
                    value = it[FactsTable.value],
                    updatedAt = Instant.fromEpochMilliseconds(it[FactsTable.updatedAt]),
                )
            }
    }
}
