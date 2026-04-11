package com.ai.challenge.core.agent

import arrow.core.Either
import com.ai.challenge.core.cost.CostDetails
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.token.TokenDetails
import com.ai.challenge.core.turn.TurnId

interface UsageTracker {
    suspend fun getTokensByTurn(turnId: TurnId): Either<AgentError, TokenDetails>
    suspend fun getTokensBySession(sessionId: AgentSessionId): Either<AgentError, Map<TurnId, TokenDetails>>
    suspend fun getSessionTotalTokens(sessionId: AgentSessionId): Either<AgentError, TokenDetails>
    suspend fun getCostByTurn(turnId: TurnId): Either<AgentError, CostDetails>
    suspend fun getCostBySession(sessionId: AgentSessionId): Either<AgentError, Map<TurnId, CostDetails>>
    suspend fun getSessionTotalCost(sessionId: AgentSessionId): Either<AgentError, CostDetails>
}
