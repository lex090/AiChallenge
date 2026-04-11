package com.ai.challenge.cost.repository

import com.ai.challenge.core.cost.CostDetails
import com.ai.challenge.core.cost.CostDetailsRepository
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.turn.TurnRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedCostRepository(
    private val database: Database,
    private val turnRepository: TurnRepository,
) : CostDetailsRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(CostDetailsTable)
        }
    }

    override suspend fun record(turnId: TurnId, details: CostDetails) {
        val turn = turnRepository.get(turnId = turnId)
            ?: error("Turn not found for turnId=${turnId.value}")
        transaction(database) {
            CostDetailsTable.insert {
                it[CostDetailsTable.turnId] = turnId.value
                it[CostDetailsTable.sessionId] = turn.sessionId.value
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

    override suspend fun getBySession(sessionId: AgentSessionId): Map<TurnId, CostDetails> = transaction(database) {
        CostDetailsTable.selectAll()
            .where { CostDetailsTable.sessionId eq sessionId.value }
            .associate { row ->
                TurnId(row[CostDetailsTable.turnId]) to row.toCostDetails()
            }
    }

    override suspend fun getSessionTotal(sessionId: AgentSessionId): CostDetails =
        getBySession(sessionId).values.fold(CostDetails(totalCost = 0.0, upstreamCost = 0.0, upstreamPromptCost = 0.0, upstreamCompletionsCost = 0.0)) { acc, c -> acc + c }

    private fun ResultRow.toCostDetails() = CostDetails(
        totalCost = this[CostDetailsTable.totalCost],
        upstreamCost = this[CostDetailsTable.upstreamCost],
        upstreamPromptCost = this[CostDetailsTable.upstreamPromptCost],
        upstreamCompletionsCost = this[CostDetailsTable.upstreamCompletionsCost],
    )
}
