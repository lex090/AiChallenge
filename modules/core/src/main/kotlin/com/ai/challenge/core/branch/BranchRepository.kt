package com.ai.challenge.core.branch

import com.ai.challenge.core.session.AgentSessionId

interface BranchRepository {
    suspend fun create(branch: Branch): BranchId
    suspend fun get(branchId: BranchId): Branch?
    suspend fun getBySession(sessionId: AgentSessionId): List<Branch>
    suspend fun getMainBranch(sessionId: AgentSessionId): Branch?
    suspend fun getActiveBranch(sessionId: AgentSessionId): Branch?
    suspend fun setActive(sessionId: AgentSessionId, branchId: BranchId)
    suspend fun delete(branchId: BranchId)
}
