package com.ai.challenge.core.summary

import com.ai.challenge.core.session.AgentSessionId

interface SummaryRepository {
    suspend fun save(summary: Summary)
    suspend fun getBySession(sessionId: AgentSessionId): List<Summary>
    suspend fun deleteBySession(sessionId: AgentSessionId)
}
