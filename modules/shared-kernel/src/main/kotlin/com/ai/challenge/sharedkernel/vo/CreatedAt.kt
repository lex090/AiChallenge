package com.ai.challenge.sharedkernel.vo

import kotlin.time.Instant

/**
 * Value Object -- moment of entity creation.
 *
 * Set once at creation, never changes. Separated from [UpdatedAt]
 * to express different domain semantics -- creation is an immutable fact,
 * update is a mutable timestamp that changes on each mutation.
 */
@JvmInline
value class CreatedAt(val value: Instant)
