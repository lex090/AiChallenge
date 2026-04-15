package com.ai.challenge.contextmanagement.memory.impl

import com.ai.challenge.contextmanagement.memory.FactMemoryProvider
import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.model.Fact
import com.ai.challenge.contextmanagement.repository.FactRepository
import com.ai.challenge.sharedkernel.identity.AgentSessionId

/**
 * Default implementation of [FactMemoryProvider].
 *
 * Delegates to [FactRepository] for persistence.
 * Translates [MemoryScope] to [AgentSessionId].
 */
class DefaultFactMemoryProvider(
    private val factRepository: FactRepository,
) : FactMemoryProvider {

    override suspend fun get(scope: MemoryScope): List<Fact> {
        val sessionId = scope.toSessionId()
        return factRepository.getBySession(sessionId = sessionId)
    }

    override suspend fun replace(scope: MemoryScope, facts: List<Fact>) {
        val sessionId = scope.toSessionId()
        if (facts.isEmpty()) {
            factRepository.deleteBySession(sessionId = sessionId)
        } else {
            factRepository.save(sessionId = sessionId, facts = facts)
        }
    }

    override suspend fun clear(scope: MemoryScope) {
        when (scope) {
            is MemoryScope.Session -> factRepository.deleteBySession(sessionId = scope.sessionId)
            is MemoryScope.Project -> Unit
        }
    }

    private fun MemoryScope.toSessionId(): AgentSessionId = when (this) {
        is MemoryScope.Session -> sessionId
        is MemoryScope.Project -> error("FactMemoryProvider does not support Project scope")
    }
}
