package com.ai.challenge.core

interface BranchRepository {
    suspend fun createBranch(branch: Branch): BranchId
    suspend fun getBranches(sessionId: SessionId): List<Branch>
    suspend fun getBranch(branchId: BranchId): Branch?
    suspend fun deleteBranch(branchId: BranchId): Boolean
    suspend fun getActiveBranch(sessionId: SessionId): Branch?
    suspend fun setActiveBranch(sessionId: SessionId, branchId: BranchId?)
    suspend fun getTurnsForBranch(branchId: BranchId): List<Turn>
    suspend fun appendTurnToBranch(branchId: BranchId, turn: Turn): TurnId
}
