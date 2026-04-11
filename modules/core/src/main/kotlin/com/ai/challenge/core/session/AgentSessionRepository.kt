package com.ai.challenge.core.session

interface AgentSessionRepository {
    suspend fun save(session: AgentSession): AgentSessionId
    suspend fun get(id: AgentSessionId): AgentSession?
    suspend fun delete(id: AgentSessionId): Boolean
    suspend fun list(): List<AgentSession>
    suspend fun update(session: AgentSession)
}
