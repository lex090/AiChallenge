package com.ai.challenge.context

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.branch.TurnSequence
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
import java.math.BigDecimal
import kotlin.time.Clock

internal val ZERO_USAGE = UsageRecord(
    promptTokens = TokenCount(value = 0),
    completionTokens = TokenCount(value = 0),
    cachedTokens = TokenCount(value = 0),
    cacheWriteTokens = TokenCount(value = 0),
    reasoningTokens = TokenCount(value = 0),
    totalCost = Cost(value = BigDecimal.ZERO),
    upstreamCost = Cost(value = BigDecimal.ZERO),
    upstreamPromptCost = Cost(value = BigDecimal.ZERO),
    upstreamCompletionsCost = Cost(value = BigDecimal.ZERO),
)

internal class InMemoryAgentSessionRepository : AgentSessionRepository {
    private val sessions = mutableMapOf<AgentSessionId, AgentSession>()
    private val branches = mutableMapOf<BranchId, Branch>()
    private val turnsByBranch = mutableMapOf<BranchId, MutableList<Turn>>()
    private val turnsById = mutableMapOf<TurnId, Turn>()

    fun addSession(session: AgentSession) {
        sessions[session.id] = session
    }

    override suspend fun save(session: AgentSession): AgentSession {
        sessions[session.id] = session
        return session
    }
    override suspend fun get(id: AgentSessionId): AgentSession? = sessions[id]
    override suspend fun delete(id: AgentSessionId) { sessions.remove(id) }
    override suspend fun list(): List<AgentSession> = sessions.values.toList()
    override suspend fun update(session: AgentSession): AgentSession {
        sessions[session.id] = session
        return session
    }

    override suspend fun createBranch(branch: Branch): Branch {
        branches[branch.id] = branch
        turnsByBranch.putIfAbsent(branch.id, mutableListOf())
        return branch
    }
    override suspend fun getBranches(sessionId: AgentSessionId): List<Branch> =
        branches.values.filter { it.sessionId == sessionId }
    override suspend fun getBranch(branchId: BranchId): Branch? = branches[branchId]
    override suspend fun getMainBranch(sessionId: AgentSessionId): Branch? =
        branches.values.firstOrNull { it.sessionId == sessionId && it.isMain }
    override suspend fun deleteBranch(branchId: BranchId) { branches.remove(branchId) }
    override suspend fun deleteTurnsByBranch(branchId: BranchId) { turnsByBranch[branchId]?.clear() }

    override suspend fun appendTurn(branchId: BranchId, turn: Turn): Turn {
        turnsById[turn.id] = turn
        turnsByBranch.getOrPut(branchId) { mutableListOf() }.add(turn)
        val branch = branches[branchId]
        if (branch != null) {
            branches[branchId] = branch.copy(turnSequence = TurnSequence(values = branch.turnSequence.values + turn.id))
        }
        return turn
    }
    override suspend fun getTurnsByBranch(branchId: BranchId): List<Turn> {
        val branch = branches[branchId] ?: return emptyList()
        return branch.turnSequence.values.mapNotNull { turnsById[it] }
    }
    override suspend fun getTurn(turnId: TurnId): Turn? = turnsById[turnId]
}

internal fun createTestSession(
    sessionId: AgentSessionId,
    contextManagementType: ContextManagementType,
): AgentSession {
    val now = Clock.System.now()
    return AgentSession(
        id = sessionId,
        title = SessionTitle(value = "test"),
        contextManagementType = contextManagementType,
        createdAt = CreatedAt(value = now),
        updatedAt = UpdatedAt(value = now),
    )
}

internal fun createTestBranch(
    id: BranchId,
    sessionId: AgentSessionId,
    sourceTurnId: TurnId?,
    turnIds: List<TurnId>,
): Branch {
    return Branch(
        id = id,
        sessionId = sessionId,
        sourceTurnId = sourceTurnId,
        turnSequence = TurnSequence(values = turnIds),
        createdAt = CreatedAt(value = Clock.System.now()),
    )
}

internal fun createTestTurn(
    sessionId: AgentSessionId,
    userMessage: String,
    assistantMessage: String,
): Turn {
    return Turn(
        id = TurnId.generate(),
        sessionId = sessionId,
        userMessage = MessageContent(value = userMessage),
        assistantMessage = MessageContent(value = assistantMessage),
        usage = ZERO_USAGE,
        createdAt = CreatedAt(value = Clock.System.now()),
    )
}
