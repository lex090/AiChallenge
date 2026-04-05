package com.ai.challenge.core

interface TurnRepository {
    suspend fun append(sessionId: SessionId, turn: Turn): TurnId
    suspend fun getBySession(sessionId: SessionId, limit: Int? = null): List<Turn>
    suspend fun get(turnId: TurnId): Turn?
}
