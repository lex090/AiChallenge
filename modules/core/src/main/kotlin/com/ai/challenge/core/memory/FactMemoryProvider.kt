package com.ai.challenge.core.memory

import com.ai.challenge.core.fact.Fact

/**
 * Fact memory provider — replace-all write semantics.
 *
 * Invariant: [replace] deletes all existing facts for the scope
 * and writes the new list atomically.
 */
interface FactMemoryProvider : MemoryProvider<List<Fact>> {
    suspend fun replace(scope: MemoryScope, facts: List<Fact>)
}
