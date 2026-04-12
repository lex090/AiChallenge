package com.ai.challenge.core.memory

/**
 * Port for accessing agent memory (E6: Domain Service).
 *
 * Registry-based facade: type-safe provider lookup by [MemoryType],
 * scope-wide lifecycle management via [clearScope].
 *
 * Defined in core, implemented in domain/memory-service.
 */
interface MemoryService {
    fun <P : MemoryProvider<*>> provider(type: MemoryType<P>): P
    suspend fun clearScope(scope: MemoryScope)
}
