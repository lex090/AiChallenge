package com.ai.challenge.core.shared

import kotlin.time.Instant

/**
 * Moment of entity creation.
 * Set once at creation, never changes.
 * Separated from [UpdatedAt] to express different semantics —
 * creation and update are different domain events.
 */
@JvmInline
value class CreatedAt(val value: Instant)
