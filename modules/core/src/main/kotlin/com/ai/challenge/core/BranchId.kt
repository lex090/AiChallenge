package com.ai.challenge.core

import java.util.UUID

@JvmInline
value class BranchId(val value: String) {
    companion object {
        fun generate(): BranchId = BranchId(UUID.randomUUID().toString())
    }
}
