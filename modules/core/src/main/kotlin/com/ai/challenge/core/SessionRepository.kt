package com.ai.challenge.core

interface SessionRepository {
    suspend fun create(title: String = ""): SessionId
    suspend fun get(id: SessionId): AgentSession?
    suspend fun delete(id: SessionId): Boolean
    suspend fun list(): List<AgentSession>
    suspend fun updateTitle(id: SessionId, title: String)
}
