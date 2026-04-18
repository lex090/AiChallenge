package com.ai.challenge.conversation.model

/**
 * Value Object -- name of a [Project].
 *
 * Has no identity -- defined only by the string it wraps.
 * Immutable.
 *
 * Invariants:
 * - Must not be blank (enforced at creation site, not in VO itself,
 *   consistent with [SessionTitle] pattern).
 */
@JvmInline
value class ProjectName(val value: String)
