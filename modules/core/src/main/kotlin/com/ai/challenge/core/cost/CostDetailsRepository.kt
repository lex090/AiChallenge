package com.ai.challenge.core.cost

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.model.Cost

interface CostDetailsRepository {
    suspend fun record(turnId: TurnId, promptCost: Cost, completionCost: Cost)
    suspend fun getByTurn(turnId: TurnId): Cost?
    suspend fun getBySession(sessionId: AgentSessionId): Map<TurnId, Cost>
    suspend fun getSessionTotal(sessionId: AgentSessionId): Cost
}
