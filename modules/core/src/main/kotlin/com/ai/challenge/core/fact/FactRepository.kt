package com.ai.challenge.core.fact

import com.ai.challenge.core.session.AgentSessionId

interface FactRepository {
    suspend fun save(facts: List<Fact>)
    suspend fun getBySession(sessionId: AgentSessionId): List<Fact>
    suspend fun deleteBySession(sessionId: AgentSessionId)
}
