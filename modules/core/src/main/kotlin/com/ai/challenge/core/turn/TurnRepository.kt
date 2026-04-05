package com.ai.challenge.core.turn

import com.ai.challenge.core.session.SessionId

interface TurnRepository {
    suspend fun append(sessionId: SessionId, turn: Turn): TurnId
    suspend fun getBySession(sessionId: SessionId, limit: Int? = null): List<Turn>
    suspend fun get(turnId: TurnId): Turn?
}
