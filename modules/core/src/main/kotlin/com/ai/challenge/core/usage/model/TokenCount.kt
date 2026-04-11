package com.ai.challenge.core.usage.model

/**
 * Token count.
 * Value object — cannot be negative.
 * Supports arithmetic for session-level aggregation.
 */
@JvmInline
value class TokenCount(val value: Int) {
    operator fun plus(other: TokenCount): TokenCount =
        TokenCount(value = value + other.value)
}
