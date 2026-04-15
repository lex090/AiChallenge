package com.ai.challenge.conversation.model

/**
 * Value Object -- name of a [User].
 *
 * Has no identity -- defined only by the string it wraps.
 * Immutable.
 *
 * Invariants:
 * - Must not be blank (enforced at creation site, not in VO itself,
 *   consistent with [ProjectName] pattern).
 */
@JvmInline
value class UserName(val value: String)
