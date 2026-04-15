package com.ai.challenge.conversation.data

import com.ai.challenge.conversation.model.AgentSession
import com.ai.challenge.conversation.model.Branch
import com.ai.challenge.conversation.model.Cost
import com.ai.challenge.conversation.model.SessionTitle
import com.ai.challenge.conversation.model.TokenCount
import com.ai.challenge.conversation.model.Turn
import com.ai.challenge.conversation.model.TurnSequence
import com.ai.challenge.conversation.model.UsageRecord
import com.ai.challenge.conversation.repository.AgentSessionRepository
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.ai.challenge.sharedkernel.identity.TurnId
import com.ai.challenge.sharedkernel.identity.UserId
import com.ai.challenge.sharedkernel.vo.ContextModeId
import com.ai.challenge.sharedkernel.vo.CreatedAt
import com.ai.challenge.sharedkernel.vo.MessageContent
import com.ai.challenge.sharedkernel.vo.UpdatedAt
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
import java.math.BigDecimal
import kotlin.time.Instant

/**
 * Exposed-based implementation of [AgentSessionRepository].
 *
 * Sole access point to the AgentSession aggregate persistence (SQLite).
 * Manages four tables: sessions, branches, turns, branch_turns.
 * Creates missing tables/columns on initialization.
 *
 * Maps [ContextModeId] to/from storage as a plain string value
 * in the `context_management_type` column.
 */
class ExposedAgentSessionRepository(
    private val database: Database,
) : AgentSessionRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(
                SessionsTable,
                TurnsTable,
                BranchesTable,
                BranchTurnsTable,
                ProjectsTable,
            )
        }
    }

    override suspend fun save(session: AgentSession): AgentSession {
        transaction(db = database) {
            SessionsTable.insert {
                it[id] = session.id.value
                it[title] = session.title.value
                it[contextManagementType] = session.contextModeId.value
                it[projectId] = session.projectId?.value
                it[userId] = session.userId?.value
                it[createdAt] = session.createdAt.value.toEpochMilliseconds()
                it[updatedAt] = session.updatedAt.value.toEpochMilliseconds()
            }
        }
        return session
    }

    override suspend fun get(id: AgentSessionId): AgentSession? = transaction(database) {
        SessionsTable.selectAll()
            .where { SessionsTable.id eq id.value }
            .singleOrNull()
            ?.toAgentSession()
    }

    override suspend fun delete(id: AgentSessionId) {
        transaction(database) {
            val branchIds = BranchesTable.selectAll()
                .where { BranchesTable.sessionId eq id.value }
                .map { it[BranchesTable.id] }
            for (branchId in branchIds) {
                BranchTurnsTable.deleteWhere { BranchTurnsTable.branchId eq branchId }
            }
            BranchesTable.deleteWhere { BranchesTable.sessionId eq id.value }
            TurnsTable.deleteWhere { TurnsTable.sessionId eq id.value }
            SessionsTable.deleteWhere { SessionsTable.id eq id.value }
        }
    }

    override suspend fun list(): List<AgentSession> = transaction(database) {
        SessionsTable.selectAll()
            .orderBy(SessionsTable.updatedAt, SortOrder.DESC)
            .map { it.toAgentSession() }
    }

    override suspend fun update(session: AgentSession): AgentSession {
        transaction(db = database) {
            SessionsTable.update(where = { SessionsTable.id eq session.id.value }) {
                it[title] = session.title.value
                it[contextManagementType] = session.contextModeId.value
                it[projectId] = session.projectId?.value
                it[userId] = session.userId?.value
                it[updatedAt] = session.updatedAt.value.toEpochMilliseconds()
            }
        }
        return session
    }

    override suspend fun createBranch(branch: Branch): Branch {
        transaction(database) {
            BranchesTable.insert {
                it[id] = branch.id.value
                it[sessionId] = branch.sessionId.value
                it[sourceTurnId] = branch.sourceTurnId?.value
                it[createdAt] = branch.createdAt.value.toEpochMilliseconds()
            }
            for ((index, turnId) in branch.turnSequence.values.withIndex()) {
                BranchTurnsTable.insert {
                    it[branchId] = branch.id.value
                    it[BranchTurnsTable.turnId] = turnId.value
                    it[orderIndex] = index
                }
            }
        }
        return branch
    }

    override suspend fun getBranches(sessionId: AgentSessionId): List<Branch> = transaction(database) {
        BranchesTable.selectAll()
            .where { BranchesTable.sessionId eq sessionId.value }
            .map { it.toBranch() }
    }

    override suspend fun getBranch(branchId: BranchId): Branch? = transaction(database) {
        BranchesTable.selectAll()
            .where { BranchesTable.id eq branchId.value }
            .singleOrNull()
            ?.toBranch()
    }

    override suspend fun getMainBranch(sessionId: AgentSessionId): Branch? = transaction(database) {
        BranchesTable.selectAll()
            .where { (BranchesTable.sessionId eq sessionId.value) and BranchesTable.sourceTurnId.isNull() }
            .singleOrNull()
            ?.toBranch()
    }

    override suspend fun deleteBranch(branchId: BranchId) {
        transaction(database) {
            BranchTurnsTable.deleteWhere { BranchTurnsTable.branchId eq branchId.value }
            BranchesTable.deleteWhere { BranchesTable.id eq branchId.value }
        }
    }

    override suspend fun deleteTurnsByBranch(branchId: BranchId) {
        transaction(database) {
            BranchTurnsTable.deleteWhere { BranchTurnsTable.branchId eq branchId.value }
        }
    }

    override suspend fun appendTurn(branchId: BranchId, turn: Turn): Turn {
        transaction(database) {
            TurnsTable.insert {
                it[id] = turn.id.value
                it[sessionId] = turn.sessionId.value
                it[userMessage] = turn.userMessage.value
                it[assistantMessage] = turn.assistantMessage.value
                it[createdAt] = turn.createdAt.value.toEpochMilliseconds()
                it[promptTokens] = turn.usage.promptTokens.value
                it[completionTokens] = turn.usage.completionTokens.value
                it[cachedTokens] = turn.usage.cachedTokens.value
                it[cacheWriteTokens] = turn.usage.cacheWriteTokens.value
                it[reasoningTokens] = turn.usage.reasoningTokens.value
                it[totalCost] = turn.usage.totalCost.value.toString()
                it[upstreamCost] = turn.usage.upstreamCost.value.toString()
                it[upstreamPromptCost] = turn.usage.upstreamPromptCost.value.toString()
                it[upstreamCompletionsCost] = turn.usage.upstreamCompletionsCost.value.toString()
            }
            val maxIndex = BranchTurnsTable.selectAll()
                .where { BranchTurnsTable.branchId eq branchId.value }
                .maxByOrNull { it[BranchTurnsTable.orderIndex] }
                ?.get(BranchTurnsTable.orderIndex)
            BranchTurnsTable.insert {
                it[BranchTurnsTable.branchId] = branchId.value
                it[turnId] = turn.id.value
                it[orderIndex] = (maxIndex ?: -1) + 1
            }
        }
        return turn
    }

    override suspend fun getTurnsByBranch(branchId: BranchId): List<Turn> = transaction(database) {
        val turnIds = BranchTurnsTable.selectAll()
            .where { BranchTurnsTable.branchId eq branchId.value }
            .orderBy(BranchTurnsTable.orderIndex, SortOrder.ASC)
            .map { it[BranchTurnsTable.turnId] }

        turnIds.mapNotNull { turnId ->
            TurnsTable.selectAll()
                .where { TurnsTable.id eq turnId }
                .singleOrNull()
                ?.toTurn()
        }
    }

    override suspend fun getTurn(turnId: TurnId): Turn? = transaction(database) {
        TurnsTable.selectAll()
            .where { TurnsTable.id eq turnId.value }
            .singleOrNull()
            ?.toTurn()
    }

    override suspend fun clearProjectId(projectId: ProjectId) {
        transaction(database) {
            SessionsTable.update(where = { SessionsTable.projectId eq projectId.value }) {
                it[SessionsTable.projectId] = null
            }
        }
    }

    override suspend fun clearUserId(userId: UserId) {
        transaction(database) {
            SessionsTable.update(where = { SessionsTable.userId eq userId.value }) {
                it[SessionsTable.userId] = null
            }
        }
    }

    private fun ResultRow.toAgentSession() = AgentSession(
        id = AgentSessionId(value = this[SessionsTable.id]),
        title = SessionTitle(value = this[SessionsTable.title]),
        contextModeId = ContextModeId(value = this[SessionsTable.contextManagementType]),
        projectId = this[SessionsTable.projectId]?.let { ProjectId(value = it) },
        userId = this[SessionsTable.userId]?.let { UserId(value = it) },
        createdAt = CreatedAt(value = Instant.fromEpochMilliseconds(this[SessionsTable.createdAt])),
        updatedAt = UpdatedAt(value = Instant.fromEpochMilliseconds(this[SessionsTable.updatedAt])),
    )

    private fun ResultRow.toBranch(): Branch {
        val branchIdValue = this[BranchesTable.id]
        val turnIds = BranchTurnsTable.selectAll()
            .where { BranchTurnsTable.branchId eq branchIdValue }
            .orderBy(BranchTurnsTable.orderIndex, SortOrder.ASC)
            .map { TurnId(value = it[BranchTurnsTable.turnId]) }
        return Branch(
            id = BranchId(value = branchIdValue),
            sessionId = AgentSessionId(value = this[BranchesTable.sessionId]),
            sourceTurnId = this[BranchesTable.sourceTurnId]?.let { TurnId(value = it) },
            turnSequence = TurnSequence(values = turnIds),
            createdAt = CreatedAt(value = Instant.fromEpochMilliseconds(this[BranchesTable.createdAt])),
        )
    }

    private fun ResultRow.toTurn() = Turn(
        id = TurnId(value = this[TurnsTable.id]),
        sessionId = AgentSessionId(value = this[TurnsTable.sessionId]),
        userMessage = MessageContent(value = this[TurnsTable.userMessage]),
        assistantMessage = MessageContent(value = this[TurnsTable.assistantMessage]),
        usage = UsageRecord(
            promptTokens = TokenCount(value = this[TurnsTable.promptTokens]),
            completionTokens = TokenCount(value = this[TurnsTable.completionTokens]),
            cachedTokens = TokenCount(value = this[TurnsTable.cachedTokens]),
            cacheWriteTokens = TokenCount(value = this[TurnsTable.cacheWriteTokens]),
            reasoningTokens = TokenCount(value = this[TurnsTable.reasoningTokens]),
            totalCost = Cost(value = BigDecimal(this[TurnsTable.totalCost])),
            upstreamCost = Cost(value = BigDecimal(this[TurnsTable.upstreamCost])),
            upstreamPromptCost = Cost(value = BigDecimal(this[TurnsTable.upstreamPromptCost])),
            upstreamCompletionsCost = Cost(value = BigDecimal(this[TurnsTable.upstreamCompletionsCost])),
        ),
        createdAt = CreatedAt(value = Instant.fromEpochMilliseconds(this[TurnsTable.createdAt])),
    )
}
