package com.ai.challenge.core.branch

import com.ai.challenge.core.turn.TurnId

interface BranchTurnRepository {
    suspend fun append(branchId: BranchId, turnId: TurnId, orderIndex: Int)
    suspend fun getTurnIds(branchId: BranchId): List<TurnId>
    suspend fun findBranchByTurnId(turnId: TurnId): BranchId?
    suspend fun getMaxOrderIndex(branchId: BranchId): Int?
    suspend fun deleteByBranch(branchId: BranchId)
}
