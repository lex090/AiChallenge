package com.ai.challenge.core.session

interface AgentSessionRepository {
    suspend fun create(title: String = ""): AgentSessionId
    suspend fun get(id: AgentSessionId): AgentSession?
    suspend fun delete(id: AgentSessionId): Boolean
    suspend fun list(): List<AgentSession>
    suspend fun updateTitle(id: AgentSessionId, title: String)
}
