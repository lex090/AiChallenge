package com.ai.challenge.contextmanagement.memory

/**
 * Domain Service -- provider for a specific memory type.
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
