package com.ai.challenge.core.agent

import arrow.core.Either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.cost.CostDetails
import com.ai.challenge.core.token.TokenDetails
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId

interface Agent {
    suspend fun send(sessionId: AgentSessionId, message: String): Either<AgentError, AgentResponse>
    suspend fun createSession(title: String): Either<AgentError, AgentSessionId>
    suspend fun deleteSession(id: AgentSessionId): Either<AgentError, Unit>
    suspend fun listSessions(): Either<AgentError, List<AgentSession>>
    suspend fun getSession(id: AgentSessionId): Either<AgentError, AgentSession>
    suspend fun updateSessionTitle(id: AgentSessionId, title: String): Either<AgentError, Unit>
    suspend fun getTurns(sessionId: AgentSessionId, limit: Int?): Either<AgentError, List<Turn>>
    suspend fun getTokensByTurn(turnId: TurnId): Either<AgentError, TokenDetails>
    suspend fun getTokensBySession(sessionId: AgentSessionId): Either<AgentError, Map<TurnId, TokenDetails>>
    suspend fun getSessionTotalTokens(sessionId: AgentSessionId): Either<AgentError, TokenDetails>
    suspend fun getCostByTurn(turnId: TurnId): Either<AgentError, CostDetails>
    suspend fun getCostBySession(sessionId: AgentSessionId): Either<AgentError, Map<TurnId, CostDetails>>
    suspend fun getSessionTotalCost(sessionId: AgentSessionId): Either<AgentError, CostDetails>
    suspend fun getContextManagementType(sessionId: AgentSessionId): Either<AgentError, ContextManagementType>
    suspend fun updateContextManagementType(sessionId: AgentSessionId, type: ContextManagementType): Either<AgentError, Unit>
    suspend fun createBranch(sessionId: AgentSessionId, name: String, parentTurnId: TurnId, fromBranchId: BranchId): Either<AgentError, BranchId>
    suspend fun deleteBranch(branchId: BranchId): Either<AgentError, Unit>
    suspend fun getBranches(sessionId: AgentSessionId): Either<AgentError, List<Branch>>
    suspend fun switchBranch(sessionId: AgentSessionId, branchId: BranchId): Either<AgentError, Unit>
    suspend fun getActiveBranch(sessionId: AgentSessionId): Either<AgentError, Branch?>
    suspend fun getActiveBranchTurns(sessionId: AgentSessionId): Either<AgentError, List<Turn>>
    suspend fun getBranchParentMap(sessionId: AgentSessionId): Either<AgentError, Map<BranchId, BranchId?>>
}
