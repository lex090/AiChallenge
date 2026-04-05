package com.ai.challenge.cost.repository

import com.ai.challenge.core.metrics.CostDetails
import com.ai.challenge.core.metrics.CostRepository
import com.ai.challenge.core.session.SessionId
import com.ai.challenge.core.turn.TurnId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedCostRepository(private val database: Database) : CostRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(CostDetailsTable)
        }
    }

    override suspend fun record(sessionId: SessionId, turnId: TurnId, details: CostDetails) {
        transaction(database) {
            CostDetailsTable.insert {
                it[CostDetailsTable.turnId] = turnId.value
                it[CostDetailsTable.sessionId] = sessionId.value
                it[totalCost] = details.totalCost
                it[upstreamCost] = details.upstreamCost
                it[upstreamPromptCost] = details.upstreamPromptCost
                it[upstreamCompletionsCost] = details.upstreamCompletionsCost
            }
        }
    }

    override suspend fun getByTurn(turnId: TurnId): CostDetails? = transaction(database) {
        CostDetailsTable.selectAll()
            .where { CostDetailsTable.turnId eq turnId.value }
            .singleOrNull()
            ?.toCostDetails()
    }

    override suspend fun getBySession(sessionId: SessionId): Map<TurnId, CostDetails> = transaction(database) {
        CostDetailsTable.selectAll()
            .where { CostDetailsTable.sessionId eq sessionId.value }
            .associate { row ->
                TurnId(row[CostDetailsTable.turnId]) to row.toCostDetails()
            }
    }

    override suspend fun getSessionTotal(sessionId: SessionId): CostDetails =
        getBySession(sessionId).values.fold(CostDetails()) { acc, c -> acc + c }

    private fun ResultRow.toCostDetails() = CostDetails(
        totalCost = this[CostDetailsTable.totalCost],
        upstreamCost = this[CostDetailsTable.upstreamCost],
        upstreamPromptCost = this[CostDetailsTable.upstreamPromptCost],
        upstreamCompletionsCost = this[CostDetailsTable.upstreamCompletionsCost],
    )
}
