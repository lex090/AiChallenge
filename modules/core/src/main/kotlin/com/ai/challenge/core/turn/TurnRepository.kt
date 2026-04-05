package com.ai.challenge.core.turn

import com.ai.challenge.core.session.AgentSessionId

interface TurnRepository {
    suspend fun append(sessionId: AgentSessionId, turn: Turn): TurnId
    suspend fun getBySession(sessionId: AgentSessionId, limit: Int? = null): List<Turn>
    suspend fun get(turnId: TurnId): Turn?
}
