package com.ai.challenge.core.summary

import com.ai.challenge.core.session.AgentSessionId

/**
 * Repository — persistence for [Summary] value objects
 * in Context Management bounded context.
 *
 * Not part of [AgentSession] aggregate — internal state of
 * SummarizeOnThreshold strategy. Accessed only by context management services.
 *
 * Append-only: [save] inserts a new summary, never replaces existing ones.
 */
interface SummaryRepository {
    suspend fun save(summary: Summary)
    suspend fun getBySession(sessionId: AgentSessionId): List<Summary>
    suspend fun deleteBySession(sessionId: AgentSessionId)
}
