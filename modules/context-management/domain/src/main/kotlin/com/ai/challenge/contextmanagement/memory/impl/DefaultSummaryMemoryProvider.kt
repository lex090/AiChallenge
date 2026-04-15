package com.ai.challenge.contextmanagement.memory.impl

import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.SummaryMemoryProvider
import com.ai.challenge.contextmanagement.model.Summary
import com.ai.challenge.contextmanagement.repository.SummaryRepository
import com.ai.challenge.sharedkernel.identity.AgentSessionId

/**
 * Default implementation of [SummaryMemoryProvider].
 *
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
        when (scope) {
            is MemoryScope.Session -> summaryRepository.deleteBySession(sessionId = scope.sessionId)
            is MemoryScope.Project -> Unit
            is MemoryScope.User -> Unit
        }
    }

    private fun MemoryScope.toSessionId(): AgentSessionId = when (this) {
        is MemoryScope.Session -> sessionId
        is MemoryScope.Project -> error("SummaryMemoryProvider does not support Project scope")
        is MemoryScope.User -> error("SummaryMemoryProvider does not support User scope")
    }
}
