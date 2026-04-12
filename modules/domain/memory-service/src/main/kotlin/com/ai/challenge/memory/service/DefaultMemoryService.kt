package com.ai.challenge.memory.service

import com.ai.challenge.core.memory.FactMemoryProvider
import com.ai.challenge.core.memory.MemoryProvider
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryService
import com.ai.challenge.core.memory.MemoryType
import com.ai.challenge.core.memory.SummaryMemoryProvider

/**
 * Default [MemoryService] implementation with registry pattern.
 * Provides type-safe provider lookup via [MemoryType] phantom type.
 * Single unchecked cast hidden inside registry — all consumers type-safe.
 */
class DefaultMemoryService(
    private val factMemoryProvider: FactMemoryProvider,
    private val summaryMemoryProvider: SummaryMemoryProvider,
) : MemoryService {

    private val providers: Map<MemoryType<*>, MemoryProvider<*>> = mapOf(
        MemoryType.Facts to factMemoryProvider,
        MemoryType.Summaries to summaryMemoryProvider,
    )

    @Suppress("UNCHECKED_CAST")
    override fun <P : MemoryProvider<*>> provider(type: MemoryType<P>): P =
        providers[type] as? P ?: error("No provider registered for memory type: $type")

    override suspend fun clearScope(scope: MemoryScope) {
        for (provider in providers.values) {
            provider.clear(scope = scope)
        }
    }
}
