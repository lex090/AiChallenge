package com.ai.challenge.session

import java.util.UUID

@JvmInline
value class TurnId(val value: String) {
    companion object {
        fun generate(): TurnId = TurnId(UUID.randomUUID().toString())
    }
}
