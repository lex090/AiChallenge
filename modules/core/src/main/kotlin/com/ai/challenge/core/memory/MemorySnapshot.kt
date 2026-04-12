package com.ai.challenge.core.memory

import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.summary.Summary

/**
 * Immutable snapshot of all memory types in a scope.
 * Value Object (E3): equality by attributes.
 *
 * Used by [GetMemoryUseCase] to return all memory at once.
 */
data class MemorySnapshot(
    val facts: List<Fact>,
    val summaries: List<Summary>,
)
