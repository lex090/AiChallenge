package com.ai.challenge.core.context

/**
 * Value Object — sealed hierarchy of context management strategies.
 *
 * Determines how conversation context is prepared before
 * each LLM call. Stored as attribute of [AgentSession].
 *
 * Each variant corresponds to a ContextStrategy implementation
 * and a ContextStrategyConfig configuration.
 *
 * [None] — full history, no processing.
 * [SummarizeOnThreshold] — compress old turns when history exceeds threshold.
 * [SlidingWindow] — keep only last N turns.
 * [StickyFacts] — extract facts via LLM, retain with recent turns.
 * [Branching] — passthrough for current branch history.
 */
sealed interface ContextManagementType {
    data object None : ContextManagementType
    data object SummarizeOnThreshold : ContextManagementType
    data object SlidingWindow : ContextManagementType
    data object StickyFacts : ContextManagementType
    data object Branching : ContextManagementType
}
