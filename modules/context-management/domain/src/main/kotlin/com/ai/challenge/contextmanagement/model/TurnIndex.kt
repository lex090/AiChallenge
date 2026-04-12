package com.ai.challenge.contextmanagement.model

/**
 * Value Object -- zero-based index of a Turn in a session's history.
 *
 * Used by [Summary] to define the range of summarized turns.
 * Has no identity -- defined solely by its integer [value].
 */
@JvmInline
value class TurnIndex(val value: Int)
