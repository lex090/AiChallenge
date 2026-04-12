package com.ai.challenge.contextmanagement.model

/**
 * Value Object -- key in a [Fact] key-value pair.
 *
 * Identifies what the fact is about (e.g., "preferred language").
 * Has no identity -- defined solely by its string [value].
 */
@JvmInline
value class FactKey(val value: String)
