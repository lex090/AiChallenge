package com.ai.challenge.core

import java.util.UUID

@JvmInline
value class SessionId(val value: String) {
    companion object {
        fun generate(): SessionId = SessionId(UUID.randomUUID().toString())
    }
}
