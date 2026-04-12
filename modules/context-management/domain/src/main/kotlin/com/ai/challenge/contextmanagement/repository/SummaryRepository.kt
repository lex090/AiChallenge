package com.ai.challenge.contextmanagement.repository

import com.ai.challenge.contextmanagement.model.Summary
import com.ai.challenge.sharedkernel.identity.AgentSessionId

/**
 * Repository -- persistence for [Summary] value objects
 * in Context Management bounded context.
 *
 * Not part of AgentSession aggregate -- internal state of
 * SummarizeOnThreshold strategy. Accessed only by context management services.
 *
 * Append-only: [save] inserts a new summary, never replaces existing ones.
 *
 * Invariants:
 * - [save] appends without modifying existing summaries.
 * - [getBySession] returns all summaries for the given session, or empty list.
 */
interface SummaryRepository {
    suspend fun save(summary: Summary)
    suspend fun getBySession(sessionId: AgentSessionId): List<Summary>
    suspend fun deleteBySession(sessionId: AgentSessionId)
}
