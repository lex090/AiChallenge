package com.ai.challenge.core

interface FactRepository {
    suspend fun getBySession(sessionId: SessionId): List<Fact>
    suspend fun save(sessionId: SessionId, facts: List<Fact>)
}
