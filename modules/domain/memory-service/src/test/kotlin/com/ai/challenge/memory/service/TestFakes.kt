package com.ai.challenge.memory.service

import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.memory.FactMemoryProvider
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.SummaryMemoryProvider
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary

internal class InMemoryFactMemoryProvider : FactMemoryProvider {
    private val store = mutableMapOf<AgentSessionId, List<Fact>>()

    override suspend fun get(scope: MemoryScope): List<Fact> = when (scope) {
        is MemoryScope.Session -> store[scope.sessionId] ?: emptyList()
    }

    override suspend fun replace(scope: MemoryScope, facts: List<Fact>) {
        when (scope) {
            is MemoryScope.Session -> store[scope.sessionId] = facts
        }
    }

    override suspend fun clear(scope: MemoryScope) {
        when (scope) {
            is MemoryScope.Session -> store.remove(key = scope.sessionId)
        }
    }
}

internal class InMemorySummaryMemoryProvider : SummaryMemoryProvider {
    private val store = mutableListOf<Summary>()

    override suspend fun get(scope: MemoryScope): List<Summary> = when (scope) {
        is MemoryScope.Session -> store.filter { it.sessionId == scope.sessionId }
    }

    override suspend fun append(scope: MemoryScope, summary: Summary) {
        store.add(element = summary)
    }

    override suspend fun delete(scope: MemoryScope, summary: Summary) {
        store.removeAll { it == summary }
    }

    override suspend fun clear(scope: MemoryScope) {
        when (scope) {
            is MemoryScope.Session -> store.removeAll { it.sessionId == scope.sessionId }
        }
    }
}
