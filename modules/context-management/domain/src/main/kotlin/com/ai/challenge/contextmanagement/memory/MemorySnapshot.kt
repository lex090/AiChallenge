package com.ai.challenge.contextmanagement.memory

import com.ai.challenge.contextmanagement.model.Fact
import com.ai.challenge.contextmanagement.model.Summary

/**
 * Value Object -- immutable snapshot of all memory types in a scope.
 *
 * Used by [GetMemoryUseCase] to return all memory at once.
 *
 * Invariants:
 * - Immutable after creation.
 * - Equality by attributes.
 */
data class MemorySnapshot(
    val facts: List<Fact>,
    val summaries: List<Summary>,
)
