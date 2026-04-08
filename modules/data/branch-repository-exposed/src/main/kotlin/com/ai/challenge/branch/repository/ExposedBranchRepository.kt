package com.ai.challenge.branch.repository

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.BranchRepository
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.time.Instant

class ExposedBranchRepository(
    private val database: Database,
) : BranchRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(BranchesTable)
        }
    }

    override suspend fun create(branch: Branch): BranchId {
        transaction(database) {
            BranchesTable.insert {
                it[id] = branch.id.value
                it[sessionId] = branch.sessionId.value
                it[name] = branch.name
                it[parentTurnId] = branch.parentTurnId?.value
                it[isActive] = branch.isActive
                it[createdAt] = branch.createdAt.toEpochMilliseconds()
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
            .where { (BranchesTable.sessionId eq sessionId.value) and BranchesTable.parentTurnId.isNull() }
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
            BranchesTable.deleteWhere { BranchesTable.id eq branchId.value }
        }
    }

    private fun ResultRow.toBranch(): Branch = Branch(
        id = BranchId(value = this[BranchesTable.id]),
        sessionId = AgentSessionId(value = this[BranchesTable.sessionId]),
        name = this[BranchesTable.name],
        parentTurnId = this[BranchesTable.parentTurnId]?.let { TurnId(value = it) },
        isActive = this[BranchesTable.isActive],
        createdAt = Instant.fromEpochMilliseconds(this[BranchesTable.createdAt]),
    )
}
