package com.ai.challenge.contextmanagement.memory

import com.ai.challenge.contextmanagement.model.Summary

/**
 * Domain Service -- summary memory provider with append-only write semantics.
 *
 * Invariant: [append] adds a new summary without touching existing ones.
 * [delete] removes a specific summary from the scope.
 */
interface SummaryMemoryProvider : MemoryProvider<List<Summary>> {
    suspend fun append(scope: MemoryScope, summary: Summary)
    suspend fun delete(scope: MemoryScope, summary: Summary)
}
