package com.ai.challenge.conversation.model

/**
 * Value Object -- count of tokens consumed by an LLM operation.
 *
 * Has no identity -- defined only by the integer it wraps.
 * Immutable. Supports arithmetic via [plus] for session-level aggregation.
 * Cannot be negative (domain invariant).
 */
@JvmInline
value class TokenCount(val value: Int) {
    operator fun plus(other: TokenCount): TokenCount =
        TokenCount(value = value + other.value)
}
