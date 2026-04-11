package com.ai.challenge.core.turn

import java.util.UUID

/**
 * Typed identifier for entity [Turn].
 *
 * Value class over String (UUID). Ensures type safety —
 * impossible to accidentally pass [AgentSessionId] or [BranchId]
 * where [TurnId] is expected.
 *
 * Generation: [generate] creates a new unique identifier.
 */
@JvmInline
value class TurnId(val value: String) {
    companion object {
        fun generate(): TurnId = TurnId(UUID.randomUUID().toString())
    }
}
