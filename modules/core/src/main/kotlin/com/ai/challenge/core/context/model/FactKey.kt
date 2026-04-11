package com.ai.challenge.core.context.model

/**
 * Value Object — key in a [Fact] key-value pair.
 * Identifies what the fact is about (e.g., "preferred language").
 */
@JvmInline
value class FactKey(val value: String)
