package com.ai.challenge.core.memory

/**
 * Provider for a specific memory type (E6: Domain Service).
 *
 * Stateless, domain-named operations. Base interface covers
 * read and cleanup. Write operations are on specific sub-interfaces
 * because different memory types have different write semantics
 * (replace-all for facts, append-only for summaries).
 */
interface MemoryProvider<T> {
    suspend fun get(scope: MemoryScope): T
    suspend fun clear(scope: MemoryScope)
}
