package com.ai.challenge.core.memory

import com.ai.challenge.core.summary.Summary

/**
 * Summary memory provider — append-only write semantics.
 *
 * Invariant: [append] adds a new summary without touching existing ones.
 * [delete] removes a specific summary from the scope.
 */
interface SummaryMemoryProvider : MemoryProvider<List<Summary>> {
    suspend fun append(scope: MemoryScope, summary: Summary)
    suspend fun delete(scope: MemoryScope, summary: Summary)
}
