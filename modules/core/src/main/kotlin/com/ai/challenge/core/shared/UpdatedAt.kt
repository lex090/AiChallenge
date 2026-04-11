package com.ai.challenge.core.shared

import kotlin.time.Instant

/**
 * Moment of last entity update.
 * Changes on each mutation of aggregate root.
 */
@JvmInline
value class UpdatedAt(val value: Instant)
