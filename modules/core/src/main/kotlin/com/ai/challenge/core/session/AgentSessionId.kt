package com.ai.challenge.core.session

import java.util.UUID

@JvmInline
value class AgentSessionId(val value: String) {
    companion object {
        fun generate(): AgentSessionId = AgentSessionId(UUID.randomUUID().toString())
    }
}
