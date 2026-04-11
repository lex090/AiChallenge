package com.ai.challenge.core.usage.model

import java.math.BigDecimal

/**
 * Monetary cost.
 * Value object based on BigDecimal — not Double.
 * Double is unsuitable for monetary calculations due to precision loss.
 */
@JvmInline
value class Cost(val value: BigDecimal) {
    operator fun plus(other: Cost): Cost =
        Cost(value = value + other.value)
}
