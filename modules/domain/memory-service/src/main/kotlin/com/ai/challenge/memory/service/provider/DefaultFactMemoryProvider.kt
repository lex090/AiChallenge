package com.ai.challenge.memory.service.provider

import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.memory.FactMemoryProvider
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.session.AgentSessionId

/**
 * Default implementation of [FactMemoryProvider].
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
        val sessionId = scope.toSessionId()
        factRepository.deleteBySession(sessionId = sessionId)
    }

    private fun MemoryScope.toSessionId(): AgentSessionId = when (this) {
        is MemoryScope.Session -> sessionId
    }
}
