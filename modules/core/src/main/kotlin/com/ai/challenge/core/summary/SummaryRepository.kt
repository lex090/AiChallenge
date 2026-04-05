package com.ai.challenge.core.summary

import com.ai.challenge.core.session.AgentSessionId

interface SummaryRepository {
    suspend fun save(sessionId: AgentSessionId, summary: Summary)
    suspend fun getBySession(sessionId: AgentSessionId): List<Summary>
}
