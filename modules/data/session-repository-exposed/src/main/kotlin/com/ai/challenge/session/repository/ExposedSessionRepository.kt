package com.ai.challenge.session.repository

import com.ai.challenge.core.AgentSession
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.SessionRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Clock
import kotlin.time.Instant

class ExposedSessionRepository(private val database: Database) : SessionRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(SessionsTable)
        }
    }

    override suspend fun create(title: String): SessionId {
        val sessionId = SessionId.generate()
        val now = Clock.System.now()
        transaction(database) {
            SessionsTable.insert {
                it[id] = sessionId.value
                it[SessionsTable.title] = title
                it[createdAt] = now.toEpochMilliseconds()
                it[updatedAt] = now.toEpochMilliseconds()
            }
        }
        return sessionId
    }

    override suspend fun get(id: SessionId): AgentSession? = transaction(database) {
        SessionsTable.selectAll()
            .where { SessionsTable.id eq id.value }
            .singleOrNull()
            ?.toAgentSession()
    }

    override suspend fun delete(id: SessionId): Boolean = transaction(database) {
        SessionsTable.deleteWhere { SessionsTable.id eq id.value } > 0
    }

    override suspend fun list(): List<AgentSession> = transaction(database) {
        SessionsTable.selectAll()
            .orderBy(SessionsTable.updatedAt, SortOrder.DESC)
            .map { it.toAgentSession() }
    }

    override suspend fun updateTitle(id: SessionId, title: String) {
        val now = Clock.System.now()
        transaction(database) {
            SessionsTable.update({ SessionsTable.id eq id.value }) {
                it[SessionsTable.title] = title
                it[updatedAt] = now.toEpochMilliseconds()
            }
        }
    }

    private fun ResultRow.toAgentSession() = AgentSession(
        id = SessionId(this[SessionsTable.id]),
        title = this[SessionsTable.title],
        createdAt = Instant.fromEpochMilliseconds(this[SessionsTable.createdAt]),
        updatedAt = Instant.fromEpochMilliseconds(this[SessionsTable.updatedAt]),
    )
}
