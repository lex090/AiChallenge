package com.ai.challenge.core.context.model

/**
 * Value Object — zero-based index of a [Turn] in a session's history.
 * Used by [Summary] to define the range of summarized turns.
 */
@JvmInline
value class TurnIndex(val value: Int)
