package com.ai.challenge.core.fact

/**
 * Value Object — classification of an extracted [Fact].
 *
 * Used by LlmFactExtractor to categorize facts
 * extracted from conversation via LLM.
 */
enum class FactCategory {
    Goal,
    Constraint,
    Preference,
    Decision,
    Agreement,
}
