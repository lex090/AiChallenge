package com.ai.challenge.session.repository

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.model.BranchName
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.shared.UpdatedAt
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.model.Cost
import com.ai.challenge.core.usage.model.TokenCount
import com.ai.challenge.core.usage.model.UsageRecord
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
            )
        }
    }

    // === Session Lifecycle ===

    override suspend fun save(session: AgentSession): AgentSession {
        transaction(db = database) {
            SessionsTable.insert {
                it[id] = session.id.value
                it[title] = session.title.value
                it[contextManagementType] = session.contextManagementType.toStorageString()
                it[activeBranchId] = session.activeBranchId.value
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
                it[contextManagementType] = session.contextManagementType.toStorageString()
                it[activeBranchId] = session.activeBranchId.value
                it[updatedAt] = session.updatedAt.value.toEpochMilliseconds()
            }
        }
        return session
    }

    // === Branches ===

    override suspend fun createBranch(branch: Branch): Branch {
        transaction(database) {
            BranchesTable.insert {
                it[id] = branch.id.value
                it[sessionId] = branch.sessionId.value
                it[parentId] = branch.parentId?.value
                it[name] = branch.name.value
                it[createdAt] = branch.createdAt.value.toEpochMilliseconds()
            }
            for ((index, turnId) in branch.turnIds.withIndex()) {
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
            .where { (BranchesTable.sessionId eq sessionId.value) and BranchesTable.parentId.isNull() }
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

    // === Turns ===

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

    override suspend fun getTurns(sessionId: AgentSessionId, limit: Int?): List<Turn> = transaction(database) {
        val query = TurnsTable.selectAll()
            .where { TurnsTable.sessionId eq sessionId.value }
            .orderBy(TurnsTable.createdAt, SortOrder.ASC)

        val rows = if (limit != null) {
            val allRows = query.toList()
            if (allRows.size > limit) allRows.takeLast(limit) else allRows
        } else {
            query.toList()
        }

        rows.map { it.toTurn() }
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

    // === Mapping ===

    private fun ResultRow.toAgentSession() = AgentSession(
        id = AgentSessionId(value = this[SessionsTable.id]),
        title = SessionTitle(value = this[SessionsTable.title]),
        contextManagementType = this[SessionsTable.contextManagementType].toContextManagementType(),
        activeBranchId = BranchId(value = this[SessionsTable.activeBranchId]),
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
            parentId = this[BranchesTable.parentId]?.let { BranchId(value = it) },
            name = BranchName(value = this[BranchesTable.name]),
            turnIds = turnIds,
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

private fun ContextManagementType.toStorageString(): String = when (this) {
    is ContextManagementType.None -> "none"
    is ContextManagementType.SummarizeOnThreshold -> "summarize_on_threshold"
    is ContextManagementType.SlidingWindow -> "sliding_window"
    is ContextManagementType.StickyFacts -> "sticky_facts"
    is ContextManagementType.Branching -> "branching"
}

private fun String.toContextManagementType(): ContextManagementType = when (this) {
    "none" -> ContextManagementType.None
    "summarize_on_threshold" -> ContextManagementType.SummarizeOnThreshold
    "sliding_window" -> ContextManagementType.SlidingWindow
    "sticky_facts" -> ContextManagementType.StickyFacts
    "branching" -> ContextManagementType.Branching
    else -> ContextManagementType.None
}
