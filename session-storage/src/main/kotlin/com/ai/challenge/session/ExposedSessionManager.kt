package com.ai.challenge.session

import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object SessionsTable : Table("sessions") {
    val id = varchar("id", 36)
    val title = varchar("title", 255).default("")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object TurnsTable : Table("turns") {
    val id = integer("id").autoIncrement()
    val sessionId = varchar("session_id", 36)
        .references(SessionsTable.id, onDelete = ReferenceOption.CASCADE)
    val userMessage = text("user_message")
    val agentResponse = text("agent_response")
    val timestamp = long("timestamp")
    val promptTokens = integer("prompt_tokens").nullable()
    val completionTokens = integer("completion_tokens").nullable()
    val totalTokens = integer("total_tokens").nullable()

    override val primaryKey = PrimaryKey(id)
}

class ExposedSessionManager(private val database: Database) : AgentSessionManager {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(SessionsTable, TurnsTable)
        }
    }

    override fun createSession(title: String): SessionId {
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

    override fun getSession(id: SessionId): AgentSession? = transaction(database) {
        val row = SessionsTable.selectAll()
            .where { SessionsTable.id eq id.value }
            .singleOrNull() ?: return@transaction null

        val history = loadHistory(id)

        AgentSession(
            id = id,
            title = row[SessionsTable.title],
            createdAt = Instant.fromEpochMilliseconds(row[SessionsTable.createdAt]),
            updatedAt = Instant.fromEpochMilliseconds(row[SessionsTable.updatedAt]),
            history = history,
        )
    }

    override fun deleteSession(id: SessionId): Boolean = transaction(database) {
        SessionsTable.deleteWhere { SessionsTable.id eq id.value } > 0
    }

    override fun listSessions(): List<AgentSession> = transaction(database) {
        SessionsTable.selectAll()
            .orderBy(SessionsTable.updatedAt, SortOrder.DESC)
            .map { row ->
                AgentSession(
                    id = SessionId(row[SessionsTable.id]),
                    title = row[SessionsTable.title],
                    createdAt = Instant.fromEpochMilliseconds(row[SessionsTable.createdAt]),
                    updatedAt = Instant.fromEpochMilliseconds(row[SessionsTable.updatedAt]),
                    history = emptyList(),
                )
            }
    }

    override fun getHistory(id: SessionId, limit: Int?): List<Turn> = transaction(database) {
        loadHistory(id, limit)
    }

    override fun appendTurn(id: SessionId, turn: Turn) {
        val now = Clock.System.now()
        transaction(database) {
            TurnsTable.insert {
                it[sessionId] = id.value
                it[userMessage] = turn.userMessage
                it[agentResponse] = turn.agentResponse
                it[timestamp] = turn.timestamp.toEpochMilliseconds()
                it[promptTokens] = turn.tokenUsage.promptTokens
                it[completionTokens] = turn.tokenUsage.completionTokens
                it[totalTokens] = turn.tokenUsage.totalTokens
            }
            SessionsTable.update({ SessionsTable.id eq id.value }) {
                it[updatedAt] = now.toEpochMilliseconds()
            }
        }
    }

    override fun updateTitle(id: SessionId, title: String) {
        val now = Clock.System.now()
        transaction(database) {
            SessionsTable.update({ SessionsTable.id eq id.value }) {
                it[SessionsTable.title] = title
                it[updatedAt] = now.toEpochMilliseconds()
            }
        }
    }

    private fun loadHistory(id: SessionId, limit: Int? = null): List<Turn> {
        val query = TurnsTable.selectAll()
            .where { TurnsTable.sessionId eq id.value }
            .orderBy(TurnsTable.timestamp, SortOrder.ASC)

        val rows = if (limit != null) {
            val allRows = query.toList()
            if (allRows.size > limit) allRows.takeLast(limit) else allRows
        } else {
            query.toList()
        }

        return rows.map { row ->
            Turn(
                userMessage = row[TurnsTable.userMessage],
                agentResponse = row[TurnsTable.agentResponse],
                timestamp = Instant.fromEpochMilliseconds(row[TurnsTable.timestamp]),
                tokenUsage = TokenUsage(
                    promptTokens = row[TurnsTable.promptTokens] ?: 0,
                    completionTokens = row[TurnsTable.completionTokens] ?: 0,
                    totalTokens = row[TurnsTable.totalTokens] ?: 0,
                ),
            )
        }
    }
}
