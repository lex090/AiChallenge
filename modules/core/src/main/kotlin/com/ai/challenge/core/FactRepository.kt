package com.ai.challenge.core

interface FactRepository {
    suspend fun save(sessionId: SessionId, facts: List<Fact>)
    suspend fun getBySession(sessionId: SessionId): List<Fact>
}
