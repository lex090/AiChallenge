package com.ai.challenge.turn.repository

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.turn.TurnRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Instant

class ExposedTurnRepository(private val database: Database) : TurnRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(TurnsTable)
        }
    }

    override suspend fun append(sessionId: AgentSessionId, turn: Turn): TurnId {
        transaction(database) {
            TurnsTable.insert {
                it[id] = turn.id.value
                it[TurnsTable.sessionId] = sessionId.value
                it[userMessage] = turn.userMessage
                it[agentResponse] = turn.agentResponse
                it[timestamp] = turn.timestamp.toEpochMilliseconds()
            }
        }
        return turn.id
    }

    override suspend fun getBySession(sessionId: AgentSessionId, limit: Int?): List<Turn> = transaction(database) {
        val query = TurnsTable.selectAll()
            .where { TurnsTable.sessionId eq sessionId.value }
            .orderBy(TurnsTable.timestamp, SortOrder.ASC)

        val rows = if (limit != null) {
            val allRows = query.toList()
            if (allRows.size > limit) allRows.takeLast(limit) else allRows
        } else {
            query.toList()
        }

        rows.map { it.toTurn() }
    }

    override suspend fun get(turnId: TurnId): Turn? = transaction(database) {
        TurnsTable.selectAll()
            .where { TurnsTable.id eq turnId.value }
            .singleOrNull()
            ?.toTurn()
    }

    private fun ResultRow.toTurn() = Turn(
        id = TurnId(this[TurnsTable.id]),
        userMessage = this[TurnsTable.userMessage],
        agentResponse = this[TurnsTable.agentResponse],
        timestamp = Instant.fromEpochMilliseconds(this[TurnsTable.timestamp]),
    )
}
