package com.ai.challenge.contextmanagement.model

/**
 * Value Object -- text content of a conversation [Summary].
 *
 * Contains the compressed representation of a range of turns.
 * Has no identity -- defined solely by its string [value].
 */
@JvmInline
value class SummaryContent(val value: String)
