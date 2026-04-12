package com.ai.challenge.contextmanagement.model

/**
 * Value Object -- configuration for each context management strategy.
 *
 * Each variant holds the tuneable parameters for its corresponding
 * [ContextManagementType]. Stored separately from the type itself
 * so the same strategy type can be reused with different settings.
 *
 * [None] -- no parameters, full passthrough.
 * [SummarizeOnThreshold] -- compression thresholds and intervals.
 * [SlidingWindow] -- window size in turns.
 * [StickyFacts] -- how many recent turns to retain alongside facts.
 * [Branching] -- no parameters, branch-scoped passthrough.
 */
sealed interface ContextStrategyConfig {
    data object None : ContextStrategyConfig
    data class SummarizeOnThreshold(
        val maxTurnsBeforeCompression: Int,
        val retainLastTurns: Int,
        val compressionInterval: Int,
    ) : ContextStrategyConfig
    data class SlidingWindow(val windowSize: Int) : ContextStrategyConfig
    data class StickyFacts(val retainLastTurns: Int) : ContextStrategyConfig
    data object Branching : ContextStrategyConfig
}
