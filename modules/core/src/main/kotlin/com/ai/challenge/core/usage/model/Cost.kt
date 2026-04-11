package com.ai.challenge.core.usage.model

import java.math.BigDecimal

/**
 * Value Object — monetary cost of an LLM operation.
 *
 * Uses [BigDecimal] (not Double) because Double is unsuitable
 * for monetary calculations due to floating-point precision loss
 * (0.1 + 0.2 != 0.3 in IEEE 754).
 *
 * Has no identity. Immutable. Supports arithmetic via [plus].
 */
@JvmInline
value class Cost(val value: BigDecimal) {
    operator fun plus(other: Cost): Cost =
        Cost(value = value + other.value)
}
