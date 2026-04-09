package com.ai.challenge.core.fact

import java.util.UUID

@JvmInline
value class FactId(val value: String) {
    companion object {
        fun generate(): FactId = FactId(value = UUID.randomUUID().toString())
    }
}
