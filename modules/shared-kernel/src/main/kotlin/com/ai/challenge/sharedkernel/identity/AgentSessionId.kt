package com.ai.challenge.sharedkernel.identity

import java.util.UUID

/**
 * Typed identifier for aggregate AgentSession.
 *
 * Value class over String (UUID). Ensures type safety --
 * impossible to accidentally pass [BranchId] or [TurnId]
 * where [AgentSessionId] is expected.
 *
 * Generation: [generate] creates a new unique identifier.
 */
@JvmInline
value class AgentSessionId(val value: String) {
    companion object {
        fun generate(): AgentSessionId = AgentSessionId(value = UUID.randomUUID().toString())
    }
}
