package com.ai.challenge.contextmanagement.model

/**
 * Value Object -- classification of an extracted [Fact].
 *
 * Used by fact extraction to categorize facts
 * extracted from conversation via LLM.
 */
enum class FactCategory {
    Goal,
    Constraint,
    Preference,
    Decision,
    Agreement,
}
