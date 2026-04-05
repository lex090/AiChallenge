package com.ai.challenge.core

import java.util.UUID

@JvmInline
value class CheckpointId(val value: String) {
    companion object {
        fun generate(): CheckpointId = CheckpointId(UUID.randomUUID().toString())
    }
}
