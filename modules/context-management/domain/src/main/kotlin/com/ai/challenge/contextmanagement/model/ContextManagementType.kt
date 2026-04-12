package com.ai.challenge.contextmanagement.model

import com.ai.challenge.sharedkernel.vo.ContextModeId

/**
 * Value Object -- sealed hierarchy of context management strategies.
 *
 * Determines how conversation context is prepared before
 * each LLM call. Each variant corresponds to a [ContextStrategy]
 * implementation and a [ContextStrategyConfig] configuration.
 *
 * Maps to/from [ContextModeId] for cross-context communication:
 * Conversation bounded context stores opaque [ContextModeId],
 * Context Management maps it to a concrete type internally.
 *
 * [None] -- full history, no processing.
 * [SummarizeOnThreshold] -- compress old turns when history exceeds threshold.
 * [SlidingWindow] -- keep only last N turns.
 * [StickyFacts] -- extract facts via LLM, retain with recent turns.
 * [Branching] -- passthrough for current branch history.
 */
sealed interface ContextManagementType {
    val modeId: ContextModeId

    data object None : ContextManagementType {
        override val modeId = ContextModeId(value = "none")
    }

    data object SummarizeOnThreshold : ContextManagementType {
        override val modeId = ContextModeId(value = "summarize_on_threshold")
    }

    data object SlidingWindow : ContextManagementType {
        override val modeId = ContextModeId(value = "sliding_window")
    }

    data object StickyFacts : ContextManagementType {
        override val modeId = ContextModeId(value = "sticky_facts")
    }

    data object Branching : ContextManagementType {
        override val modeId = ContextModeId(value = "branching")
    }

    companion object {
        private val byModeId = listOf(None, SummarizeOnThreshold, SlidingWindow, StickyFacts, Branching)
            .associateBy { it.modeId.value }

        fun fromModeId(contextModeId: ContextModeId): ContextManagementType? =
            byModeId[contextModeId.value]

        fun allModeIds(): List<ContextModeId> = byModeId.values.map { it.modeId }
    }
}
