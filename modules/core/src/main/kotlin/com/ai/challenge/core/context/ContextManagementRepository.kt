package com.ai.challenge.core.context

import com.ai.challenge.core.session.AgentSessionId

interface ContextManagementRepository {
    suspend fun save(sessionId: AgentSessionId, type: ContextManagementType)
    suspend fun getBySession(sessionId: AgentSessionId): ContextManagementType
    suspend fun delete(sessionId: AgentSessionId)
}
