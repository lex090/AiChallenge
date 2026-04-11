package com.ai.challenge.core.shared

import kotlin.time.Instant

/**
 * Value Object — moment of last aggregate mutation.
 *
 * Changes on each mutation of the aggregate root.
 * Only present on mutable entities ([AgentSession]).
 * Immutable entities ([Turn]) have only [CreatedAt].
 */
@JvmInline
value class UpdatedAt(val value: Instant)
