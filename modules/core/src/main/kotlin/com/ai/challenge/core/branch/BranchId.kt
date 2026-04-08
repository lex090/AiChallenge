package com.ai.challenge.core.branch

import java.util.UUID

@JvmInline
value class BranchId(val value: String) {
    companion object {
        fun generate(): BranchId = BranchId(value = UUID.randomUUID().toString())
    }
}
