package com.ai.challenge.branch.repository

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.BranchRepository
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.time.Instant

private object BranchTurnsTable : Table("branch_turns") {
    val branchId = varchar("branch_id", 36)
    val turnId = varchar("turn_id", 36)
    val orderIndex = integer("order_index")

    override val primaryKey = PrimaryKey(branchId, turnId)
}

class ExposedBranchRepository(
    private val database: Database,
) : BranchRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(BranchesTable, BranchTurnsTable)
        }
    }

    override suspend fun create(branch: Branch): BranchId {
        transaction(database) {
            BranchesTable.insert {
                it[id] = branch.id.value
                it[sessionId] = branch.sessionId.value
                it[name] = branch.name
                it[parentBranchId] = branch.parentBranchId?.value
                it[isActive] = branch.isActive
                it[createdAt] = branch.createdAt.toEpochMilliseconds()
            }
            for ((index, turnId) in branch.turnIds.withIndex()) {
                BranchTurnsTable.insert {
                    it[BranchTurnsTable.branchId] = branch.id.value
                    it[BranchTurnsTable.turnId] = turnId.value
                    it[orderIndex] = index
                }
            }
        }
        return branch.id
    }

    override suspend fun get(branchId: BranchId): Branch? = transaction(database) {
        BranchesTable.selectAll()
            .where { BranchesTable.id eq branchId.value }
            .singleOrNull()
            ?.toBranch()
    }

    override suspend fun getBySession(sessionId: AgentSessionId): List<Branch> = transaction(database) {
        BranchesTable.selectAll()
            .where { BranchesTable.sessionId eq sessionId.value }
            .map { it.toBranch() }
    }

    override suspend fun getMainBranch(sessionId: AgentSessionId): Branch? = transaction(database) {
        BranchesTable.selectAll()
            .where { (BranchesTable.sessionId eq sessionId.value) and BranchesTable.parentBranchId.isNull() }
            .singleOrNull()
            ?.toBranch()
    }

    override suspend fun getActiveBranch(sessionId: AgentSessionId): Branch? = transaction(database) {
        BranchesTable.selectAll()
            .where { (BranchesTable.sessionId eq sessionId.value) and (BranchesTable.isActive eq true) }
            .singleOrNull()
            ?.toBranch()
    }

    override suspend fun setActive(sessionId: AgentSessionId, branchId: BranchId) {
        transaction(database) {
            BranchesTable.update(where = { BranchesTable.sessionId eq sessionId.value }) {
                it[isActive] = false
            }
            BranchesTable.update(where = { BranchesTable.id eq branchId.value }) {
                it[isActive] = true
            }
        }
    }

    override suspend fun delete(branchId: BranchId) {
        transaction(database) {
            BranchTurnsTable.deleteWhere { BranchTurnsTable.branchId eq branchId.value }
            BranchesTable.deleteWhere { BranchesTable.id eq branchId.value }
        }
    }

    override suspend fun appendTurn(branchId: BranchId, turnId: TurnId) {
        transaction(database) {
            val maxIndex = BranchTurnsTable.selectAll()
                .where { BranchTurnsTable.branchId eq branchId.value }
                .maxByOrNull { it[BranchTurnsTable.orderIndex] }
                ?.get(BranchTurnsTable.orderIndex)
            BranchTurnsTable.insert {
                it[BranchTurnsTable.branchId] = branchId.value
                it[BranchTurnsTable.turnId] = turnId.value
                it[orderIndex] = (maxIndex ?: -1) + 1
            }
        }
    }

    override suspend fun deleteTurnsByBranch(branchId: BranchId) {
        transaction(database) {
            BranchTurnsTable.deleteWhere { BranchTurnsTable.branchId eq branchId.value }
        }
    }

    private fun ResultRow.toBranch(): Branch {
        val branchIdValue = this[BranchesTable.id]
        val turnIds = BranchTurnsTable.selectAll()
            .where { BranchTurnsTable.branchId eq branchIdValue }
            .orderBy(BranchTurnsTable.orderIndex, SortOrder.ASC)
            .map { TurnId(value = it[BranchTurnsTable.turnId]) }
        return Branch(
            id = BranchId(value = branchIdValue),
            sessionId = AgentSessionId(value = this[BranchesTable.sessionId]),
            name = this[BranchesTable.name],
            parentBranchId = this[BranchesTable.parentBranchId]?.let { BranchId(value = it) },
            isActive = this[BranchesTable.isActive],
            turnIds = turnIds,
            createdAt = Instant.fromEpochMilliseconds(this[BranchesTable.createdAt]),
        )
    }
}
