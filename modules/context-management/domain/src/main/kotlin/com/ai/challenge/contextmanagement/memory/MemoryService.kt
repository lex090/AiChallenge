package com.ai.challenge.contextmanagement.memory

/**
 * Domain Service -- registry-based facade for agent memory.
 *
 * Type-safe provider lookup by [MemoryType],
 * scope-wide lifecycle management via [clearScope].
 *
 * Invariants:
 * - Every [MemoryType] must have a registered provider.
 * - [clearScope] iterates all providers.
 */
interface MemoryService {
    fun <P : MemoryProvider<*>> provider(type: MemoryType<P>): P
    suspend fun clearScope(scope: MemoryScope)
}
