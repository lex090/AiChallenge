package com.ai.challenge.conversation.model

/**
 * Value Object -- user preferences stored as a free-form string.
 *
 * Has no identity -- defined only by the string it wraps.
 * Immutable.
 *
 * Invariants:
 * - Content may be empty (user has not configured preferences yet).
 */
@JvmInline
value class UserPreferences(val value: String)
