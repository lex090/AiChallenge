package com.ai.challenge.core.agent

import arrow.core.Either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId

interface BranchManager {
    suspend fun createBranch(sessionId: AgentSessionId, name: String, parentTurnId: TurnId, fromBranchId: BranchId): Either<AgentError, BranchId>
    suspend fun deleteBranch(branchId: BranchId): Either<AgentError, Unit>
    suspend fun getBranches(sessionId: AgentSessionId): Either<AgentError, List<Branch>>
    suspend fun switchBranch(sessionId: AgentSessionId, branchId: BranchId): Either<AgentError, Unit>
    suspend fun getActiveBranch(sessionId: AgentSessionId): Either<AgentError, Branch?>
    suspend fun getActiveBranchTurns(sessionId: AgentSessionId): Either<AgentError, List<Turn>>
    suspend fun getBranchParentMap(sessionId: AgentSessionId): Either<AgentError, Map<BranchId, BranchId?>>
}
