package com.ai.challenge.branch.repository

import com.ai.challenge.core.Branch
import com.ai.challenge.core.BranchId
import com.ai.challenge.core.BranchNode
import com.ai.challenge.core.BranchRepository
import com.ai.challenge.core.BranchTree
import com.ai.challenge.core.CheckpointNode
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn
import com.ai.challenge.core.TurnId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import kotlin.time.Instant

class ExposedBranchRepository(private val database: Database) : BranchRepository {

    init {
        transaction(database) {
            SchemaUtils.create(
                BranchesTable,
                BranchTurnsTable,
                ActiveBranchesTable,
            )
        }
    }

    override suspend fun createBranch(
        sessionId: SessionId,
        name: String,
        checkpointTurnIndex: Int,
    ): Branch {
        val branch = Branch(
            sessionId = sessionId,
            name = name,
            checkpointTurnIndex = checkpointTurnIndex,
        )
        transaction(database) {
            BranchesTable.insert {
                it[id] = branch.id.value
                it[BranchesTable.sessionId] = sessionId.value
                it[BranchesTable.name] = name
                it[BranchesTable.checkpointTurnIndex] = checkpointTurnIndex
                it[createdAt] = branch.createdAt.toEpochMilliseconds()
            }
        }
        return branch
    }

    override suspend fun getBranch(branchId: BranchId): Branch? = transaction(database) {
        BranchesTable.selectAll()
            .where { BranchesTable.id eq branchId.value }
            .firstOrNull()
            ?.toBranch()
    }

    override suspend fun getBranches(sessionId: SessionId): List<Branch> = transaction(database) {
        BranchesTable.selectAll()
            .where { BranchesTable.sessionId eq sessionId.value }
            .map { it.toBranch() }
    }

    override suspend fun getActiveBranch(sessionId: SessionId): BranchId? = transaction(database) {
        ActiveBranchesTable.selectAll()
            .where { ActiveBranchesTable.sessionId eq sessionId.value }
            .firstOrNull()
            ?.let { BranchId(it[ActiveBranchesTable.branchId]) }
    }

    override suspend fun setActiveBranch(sessionId: SessionId, branchId: BranchId?) {
        transaction(database) {
            if (branchId == null) {
                ActiveBranchesTable.deleteWhere { ActiveBranchesTable.sessionId eq sessionId.value }
            } else {
                ActiveBranchesTable.upsert {
                    it[ActiveBranchesTable.sessionId] = sessionId.value
                    it[ActiveBranchesTable.branchId] = branchId.value
                }
            }
        }
    }

    override suspend fun getBranchTree(sessionId: SessionId, mainTurnCount: Int): BranchTree {
        val branches = getBranches(sessionId)
        val checkpoints = branches
            .groupBy { it.checkpointTurnIndex }
            .map { (turnIndex, branchesAtCheckpoint) ->
                CheckpointNode(
                    turnIndex = turnIndex,
                    branches = branchesAtCheckpoint.map { branch ->
                        val turnCount = transaction(database) {
                            BranchTurnsTable.selectAll()
                                .where { BranchTurnsTable.branchId eq branch.id.value }
                                .count()
                                .toInt()
                        }
                        BranchNode(branch = branch, turnCount = turnCount)
                    },
                )
            }
            .sortedBy { it.turnIndex }
        return BranchTree(
            sessionId = sessionId,
            mainTurnCount = mainTurnCount,
            checkpoints = checkpoints,
        )
    }

    override suspend fun appendBranchTurn(branchId: BranchId, turn: Turn): TurnId {
        val sortOrder = transaction(database) {
            BranchTurnsTable.selectAll()
                .where { BranchTurnsTable.branchId eq branchId.value }
                .count()
                .toInt()
        }
        transaction(database) {
            BranchTurnsTable.insert {
                it[id] = turn.id.value
                it[BranchTurnsTable.branchId] = branchId.value
                it[userMessage] = turn.userMessage
                it[agentResponse] = turn.agentResponse
                it[timestamp] = turn.timestamp.toEpochMilliseconds()
                it[BranchTurnsTable.sortOrder] = sortOrder
            }
        }
        return turn.id
    }

    override suspend fun getBranchTurns(branchId: BranchId): List<Turn> = transaction(database) {
        BranchTurnsTable.selectAll()
            .where { BranchTurnsTable.branchId eq branchId.value }
            .orderBy(BranchTurnsTable.sortOrder)
            .map { it.toTurn() }
    }

    private fun ResultRow.toBranch() = Branch(
        id = BranchId(this[BranchesTable.id]),
        sessionId = SessionId(this[BranchesTable.sessionId]),
        name = this[BranchesTable.name],
        checkpointTurnIndex = this[BranchesTable.checkpointTurnIndex],
        createdAt = Instant.fromEpochMilliseconds(this[BranchesTable.createdAt]),
    )

    private fun ResultRow.toTurn() = Turn(
        id = TurnId(this[BranchTurnsTable.id]),
        userMessage = this[BranchTurnsTable.userMessage],
        agentResponse = this[BranchTurnsTable.agentResponse],
        timestamp = Instant.fromEpochMilliseconds(this[BranchTurnsTable.timestamp]),
    )
}
