package com.ai.challenge.session.repository

import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.session.AgentSessionRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Instant

class ExposedSessionRepository(private val database: Database) : AgentSessionRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(SessionsTable)
        }
    }

    override suspend fun save(session: AgentSession): AgentSessionId {
        transaction(db = database) {
            SessionsTable.insert {
                it[id] = session.id.value
                it[title] = session.title
                it[createdAt] = session.createdAt.toEpochMilliseconds()
                it[updatedAt] = session.updatedAt.toEpochMilliseconds()
            }
        }
        return session.id
    }

    override suspend fun get(id: AgentSessionId): AgentSession? = transaction(database) {
        SessionsTable.selectAll()
            .where { SessionsTable.id eq id.value }
            .singleOrNull()
            ?.toAgentSession()
    }

    override suspend fun delete(id: AgentSessionId): Boolean = transaction(database) {
        SessionsTable.deleteWhere { SessionsTable.id eq id.value } > 0
    }

    override suspend fun list(): List<AgentSession> = transaction(database) {
        SessionsTable.selectAll()
            .orderBy(SessionsTable.updatedAt, SortOrder.DESC)
            .map { it.toAgentSession() }
    }

    override suspend fun update(session: AgentSession) {
        transaction(db = database) {
            SessionsTable.update({ SessionsTable.id eq session.id.value }) {
                it[title] = session.title
                it[updatedAt] = session.updatedAt.toEpochMilliseconds()
            }
        }
    }

    private fun ResultRow.toAgentSession() = AgentSession(
        id = AgentSessionId(this[SessionsTable.id]),
        title = this[SessionsTable.title],
        createdAt = Instant.fromEpochMilliseconds(this[SessionsTable.createdAt]),
        updatedAt = Instant.fromEpochMilliseconds(this[SessionsTable.updatedAt]),
    )
}
