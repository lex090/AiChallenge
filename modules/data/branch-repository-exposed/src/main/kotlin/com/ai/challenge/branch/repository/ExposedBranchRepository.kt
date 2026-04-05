package com.ai.challenge.branch.repository

import com.ai.challenge.core.Branch
import com.ai.challenge.core.BranchId
import com.ai.challenge.core.BranchRepository
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn
import com.ai.challenge.core.TurnId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.time.Instant

class ExposedBranchRepository(private val database: Database) : BranchRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(BranchesTable, BranchTurnsTable)
        }
    }

    override suspend fun createBranch(branch: Branch): BranchId {
        transaction(database) {
            BranchesTable.insert {
                it[id] = branch.id.value
                it[sessionId] = branch.sessionId.value
                it[name] = branch.name
                it[checkpointTurnIndex] = branch.checkpointTurnIndex
                it[parentBranchId] = branch.parentBranchId?.value
                it[isActive] = false
                it[createdAt] = branch.createdAt.toEpochMilliseconds()
            }
        }
        return branch.id
    }

    override suspend fun getBranches(sessionId: SessionId): List<Branch> = transaction(database) {
        BranchesTable.selectAll()
            .where { BranchesTable.sessionId eq sessionId.value }
            .orderBy(BranchesTable.createdAt, SortOrder.ASC)
            .map { it.toBranch() }
    }

    override suspend fun getBranch(branchId: BranchId): Branch? = transaction(database) {
        BranchesTable.selectAll()
            .where { BranchesTable.id eq branchId.value }
            .singleOrNull()
            ?.toBranch()
    }

    override suspend fun deleteBranch(branchId: BranchId): Boolean = transaction(database) {
        BranchTurnsTable.deleteWhere { BranchTurnsTable.branchId eq branchId.value }
        BranchesTable.deleteWhere { id eq branchId.value } > 0
    }

    override suspend fun getActiveBranch(sessionId: SessionId): Branch? = transaction(database) {
        BranchesTable.selectAll()
            .where { (BranchesTable.sessionId eq sessionId.value) and (BranchesTable.isActive eq true) }
            .singleOrNull()
            ?.toBranch()
    }

    override suspend fun setActiveBranch(sessionId: SessionId, branchId: BranchId?) {
        transaction(database) {
            BranchesTable.update({ BranchesTable.sessionId eq sessionId.value }) {
                it[isActive] = false
            }
            if (branchId != null) {
                BranchesTable.update({
                    (BranchesTable.sessionId eq sessionId.value) and (BranchesTable.id eq branchId.value)
                }) {
                    it[isActive] = true
                }
            }
        }
    }

    override suspend fun getTurnsForBranch(branchId: BranchId): List<Turn> = transaction(database) {
        BranchTurnsTable.selectAll()
            .where { BranchTurnsTable.branchId eq branchId.value }
            .orderBy(BranchTurnsTable.timestamp, SortOrder.ASC)
            .map { it.toTurn() }
    }

    override suspend fun appendTurnToBranch(branchId: BranchId, turn: Turn): TurnId {
        transaction(database) {
            BranchTurnsTable.insert {
                it[id] = turn.id.value
                it[BranchTurnsTable.branchId] = branchId.value
                it[userMessage] = turn.userMessage
                it[agentResponse] = turn.agentResponse
                it[timestamp] = turn.timestamp.toEpochMilliseconds()
            }
        }
        return turn.id
    }

    private fun ResultRow.toBranch() = Branch(
        id = BranchId(this[BranchesTable.id]),
        sessionId = SessionId(this[BranchesTable.sessionId]),
        name = this[BranchesTable.name],
        checkpointTurnIndex = this[BranchesTable.checkpointTurnIndex],
        parentBranchId = this[BranchesTable.parentBranchId]?.let { BranchId(it) },
        createdAt = Instant.fromEpochMilliseconds(this[BranchesTable.createdAt]),
    )

    private fun ResultRow.toTurn() = Turn(
        id = TurnId(this[BranchTurnsTable.id]),
        userMessage = this[BranchTurnsTable.userMessage],
        agentResponse = this[BranchTurnsTable.agentResponse],
        timestamp = Instant.fromEpochMilliseconds(this[BranchTurnsTable.timestamp]),
    )
}
