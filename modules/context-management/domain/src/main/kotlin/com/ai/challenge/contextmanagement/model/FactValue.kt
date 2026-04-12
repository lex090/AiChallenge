package com.ai.challenge.contextmanagement.model

/**
 * Value Object -- value in a [Fact] key-value pair.
 *
 * The actual content of the extracted fact.
 * Has no identity -- defined solely by its string [value].
 */
@JvmInline
value class FactValue(val value: String)
