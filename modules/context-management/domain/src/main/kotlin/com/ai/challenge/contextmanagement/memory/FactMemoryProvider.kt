package com.ai.challenge.contextmanagement.memory

import com.ai.challenge.contextmanagement.model.Fact

/**
 * Domain Service -- fact memory provider with replace-all write semantics.
 *
 * Invariant: [replace] deletes all existing facts for the scope
 * and writes the new list atomically.
 */
interface FactMemoryProvider : MemoryProvider<List<Fact>> {
    suspend fun replace(scope: MemoryScope, facts: List<Fact>)
}
