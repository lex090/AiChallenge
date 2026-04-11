package com.ai.challenge.context

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.BranchRepository
import com.ai.challenge.core.branch.BranchTurnRepository
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.turn.TurnRepository

internal class InMemoryBranchRepository : BranchRepository {
    private val store = mutableListOf<Branch>()

    override suspend fun create(branch: Branch): BranchId {
        store.add(branch)
        return branch.id
    }

    override suspend fun get(branchId: BranchId): Branch? =
        store.firstOrNull { it.id == branchId }

    override suspend fun getBySession(sessionId: AgentSessionId): List<Branch> =
        store.filter { it.sessionId == sessionId }

    override suspend fun getMainBranch(sessionId: AgentSessionId): Branch? =
        store.firstOrNull { it.sessionId == sessionId && it.isMain }

    override suspend fun getActiveBranch(sessionId: AgentSessionId): Branch? =
        store.firstOrNull { it.sessionId == sessionId && it.isActive }

    override suspend fun setActive(sessionId: AgentSessionId, branchId: BranchId) {
        store.replaceAll { branch ->
            if (branch.sessionId == sessionId) branch.copy(isActive = branch.id == branchId)
            else branch
        }
    }

    override suspend fun delete(branchId: BranchId) {
        store.removeAll { it.id == branchId }
    }
}

internal class InMemoryBranchTurnRepository : BranchTurnRepository {
    private val store = mutableListOf<Triple<BranchId, TurnId, Int>>()

    override suspend fun append(branchId: BranchId, turnId: TurnId, orderIndex: Int) {
        store.add(Triple(first = branchId, second = turnId, third = orderIndex))
    }

    override suspend fun getTurnIds(branchId: BranchId): List<TurnId> =
        store.filter { it.first == branchId }.sortedBy { it.third }.map { it.second }

    override suspend fun findBranchByTurnId(turnId: TurnId): BranchId? =
        store.firstOrNull { it.second == turnId }?.first

    override suspend fun getMaxOrderIndex(branchId: BranchId): Int? =
        store.filter { it.first == branchId }.maxByOrNull { it.third }?.third

    override suspend fun deleteByBranch(branchId: BranchId) {
        store.removeAll { it.first == branchId }
    }
}

internal class InMemoryTurnRepository : TurnRepository {
    private val store = mutableListOf<Pair<AgentSessionId, Turn>>()

    override suspend fun append(sessionId: AgentSessionId, turn: Turn): TurnId {
        store.add(sessionId to turn)
        return turn.id
    }

    override suspend fun getBySession(sessionId: AgentSessionId, limit: Int?): List<Turn> =
        store.filter { it.first == sessionId }.map { it.second }

    override suspend fun get(turnId: TurnId): Turn? =
        store.map { it.second }.firstOrNull { it.id == turnId }
}
