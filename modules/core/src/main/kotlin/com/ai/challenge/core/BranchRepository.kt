package com.ai.challenge.core

interface BranchRepository {
    suspend fun createBranch(sessionId: SessionId, name: String, checkpointTurnIndex: Int): Branch
    suspend fun getBranch(branchId: BranchId): Branch?
    suspend fun getBranches(sessionId: SessionId): List<Branch>
    suspend fun getActiveBranch(sessionId: SessionId): BranchId?
    suspend fun setActiveBranch(sessionId: SessionId, branchId: BranchId?)
    suspend fun getBranchTree(sessionId: SessionId, mainTurnCount: Int): BranchTree
    suspend fun appendBranchTurn(branchId: BranchId, turn: Turn): TurnId
    suspend fun getBranchTurns(branchId: BranchId): List<Turn>
}
