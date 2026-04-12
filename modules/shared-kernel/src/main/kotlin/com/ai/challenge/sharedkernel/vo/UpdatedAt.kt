package com.ai.challenge.sharedkernel.vo

import kotlin.time.Instant

/**
 * Value Object -- moment of last aggregate mutation.
 *
 * Changes on each mutation of the aggregate root.
 * Only present on mutable entities (e.g., AgentSession).
 * Immutable entities (e.g., Turn) have only [CreatedAt].
 */
@JvmInline
value class UpdatedAt(val value: Instant)
