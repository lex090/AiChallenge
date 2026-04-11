package com.ai.challenge.context

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.BranchRepository
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

    override suspend fun appendTurn(branchId: BranchId, turnId: TurnId) {
        val index = store.indexOfFirst { it.id == branchId }
        if (index >= 0) {
            val branch = store[index]
            store[index] = branch.copy(turnIds = branch.turnIds + turnId)
        }
    }

    override suspend fun deleteTurnsByBranch(branchId: BranchId) {
        val index = store.indexOfFirst { it.id == branchId }
        if (index >= 0) {
            val branch = store[index]
            store[index] = branch.copy(turnIds = emptyList())
        }
    }
}

internal class InMemoryTurnRepository : TurnRepository {
    private val store = mutableListOf<Turn>()

    override suspend fun append(turn: Turn): TurnId {
        store.add(turn)
        return turn.id
    }

    override suspend fun getBySession(sessionId: AgentSessionId, limit: Int?): List<Turn> =
        store.filter { it.sessionId == sessionId }

    override suspend fun get(turnId: TurnId): Turn? =
        store.firstOrNull { it.id == turnId }
}
