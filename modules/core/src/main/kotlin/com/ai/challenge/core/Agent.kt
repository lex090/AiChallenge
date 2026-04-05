package com.ai.challenge.core

import arrow.core.Either

interface Agent {
    suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse>
    suspend fun createSession(title: String = ""): SessionId
    suspend fun deleteSession(id: SessionId): Boolean
    suspend fun listSessions(): List<AgentSession>
    suspend fun getSession(id: SessionId): AgentSession?
    suspend fun updateSessionTitle(id: SessionId, title: String)
    suspend fun getTurns(sessionId: SessionId, limit: Int? = null): List<Turn>
    suspend fun getEffectiveTurns(sessionId: SessionId): List<Turn>
    suspend fun getTokensByTurn(turnId: TurnId): TokenDetails?
    suspend fun getTokensBySession(sessionId: SessionId): Map<TurnId, TokenDetails>
    suspend fun getSessionTotalTokens(sessionId: SessionId): TokenDetails
    suspend fun getCostByTurn(turnId: TurnId): CostDetails?
    suspend fun getCostBySession(sessionId: SessionId): Map<TurnId, CostDetails>
    suspend fun getSessionTotalCost(sessionId: SessionId): CostDetails

    fun getContextStrategyType(): ContextStrategyType
    fun setContextStrategy(type: ContextStrategyType)
    suspend fun createCheckpoint(sessionId: SessionId): Either<AgentError, CheckpointId>
    suspend fun createBranch(sessionId: SessionId, checkpointTurnIndex: Int, name: String): Either<AgentError, BranchId>
    suspend fun switchBranch(sessionId: SessionId, branchId: BranchId): Either<AgentError, Unit>
    suspend fun deactivateBranch(sessionId: SessionId): Either<AgentError, Unit>
    suspend fun listBranches(sessionId: SessionId): Either<AgentError, List<Branch>>
    suspend fun getBranchTree(sessionId: SessionId): Either<AgentError, BranchTree>
    suspend fun getSessionFacts(sessionId: SessionId): Either<AgentError, List<Fact>>
}
