package com.ai.challenge.memory.service.usecase

import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryService
import com.ai.challenge.core.memory.MemorySnapshot
import com.ai.challenge.core.memory.MemoryType
import com.ai.challenge.core.memory.usecase.GetMemoryUseCase
import com.ai.challenge.core.session.AgentSessionId

/**
 * Default implementation of [GetMemoryUseCase].
 * Retrieves all memory types for a session via [MemoryService].
 */
class DefaultGetMemoryUseCase(
    private val memoryService: MemoryService,
) : GetMemoryUseCase {

    override suspend fun execute(sessionId: AgentSessionId): MemorySnapshot {
        val scope = MemoryScope.Session(sessionId = sessionId)
        val facts = memoryService.provider(type = MemoryType.Facts).get(scope = scope)
        val summaries = memoryService.provider(type = MemoryType.Summaries).get(scope = scope)
        return MemorySnapshot(facts = facts, summaries = summaries)
    }
}
