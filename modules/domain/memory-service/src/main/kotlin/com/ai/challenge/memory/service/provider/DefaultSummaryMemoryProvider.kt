package com.ai.challenge.memory.service.provider

import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.SummaryMemoryProvider
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository

/**
 * Default implementation of [SummaryMemoryProvider].
 * Delegates to [SummaryRepository] for persistence.
 * Translates [MemoryScope] to [AgentSessionId].
 */
class DefaultSummaryMemoryProvider(
    private val summaryRepository: SummaryRepository,
) : SummaryMemoryProvider {

    override suspend fun get(scope: MemoryScope): List<Summary> {
        val sessionId = scope.toSessionId()
        return summaryRepository.getBySession(sessionId = sessionId)
    }

    override suspend fun append(scope: MemoryScope, summary: Summary) {
        summaryRepository.save(summary = summary)
    }

    override suspend fun delete(scope: MemoryScope, summary: Summary) {
        val sessionId = scope.toSessionId()
        val existing = summaryRepository.getBySession(sessionId = sessionId)
        val remaining = existing.filter { it != summary }
        summaryRepository.deleteBySession(sessionId = sessionId)
        for (s in remaining) {
            summaryRepository.save(summary = s)
        }
    }

    override suspend fun clear(scope: MemoryScope) {
        val sessionId = scope.toSessionId()
        summaryRepository.deleteBySession(sessionId = sessionId)
    }

    private fun MemoryScope.toSessionId(): AgentSessionId = when (this) {
        is MemoryScope.Session -> sessionId
    }
}
