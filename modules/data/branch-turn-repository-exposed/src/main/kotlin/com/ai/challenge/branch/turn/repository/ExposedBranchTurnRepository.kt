package com.ai.challenge.branch.turn.repository

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.BranchTurnRepository
import com.ai.challenge.core.turn.TurnId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedBranchTurnRepository(
    private val database: Database,
) : BranchTurnRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(BranchTurnsTable)
        }
    }

    override suspend fun append(branchId: BranchId, turnId: TurnId, orderIndex: Int) {
        transaction(database) {
            BranchTurnsTable.insert {
                it[BranchTurnsTable.branchId] = branchId.value
                it[BranchTurnsTable.turnId] = turnId.value
                it[BranchTurnsTable.orderIndex] = orderIndex
            }
        }
    }

    override suspend fun getTurnIds(branchId: BranchId): List<TurnId> = transaction(database) {
        BranchTurnsTable.selectAll()
            .where { BranchTurnsTable.branchId eq branchId.value }
            .orderBy(BranchTurnsTable.orderIndex, SortOrder.ASC)
            .map { TurnId(value = it[BranchTurnsTable.turnId]) }
    }

    override suspend fun findBranchByTurnId(turnId: TurnId): BranchId? = transaction(database) {
        BranchTurnsTable.selectAll()
            .where { BranchTurnsTable.turnId eq turnId.value }
            .singleOrNull()
            ?.let { BranchId(value = it[BranchTurnsTable.branchId]) }
    }

    override suspend fun getMaxOrderIndex(branchId: BranchId): Int? = transaction(database) {
        BranchTurnsTable.selectAll()
            .where { BranchTurnsTable.branchId eq branchId.value }
            .maxByOrNull { it[BranchTurnsTable.orderIndex] }
            ?.get(BranchTurnsTable.orderIndex)
    }

    override suspend fun deleteByBranch(branchId: BranchId) {
        transaction(database) {
            BranchTurnsTable.deleteWhere { BranchTurnsTable.branchId eq branchId.value }
        }
    }
}
