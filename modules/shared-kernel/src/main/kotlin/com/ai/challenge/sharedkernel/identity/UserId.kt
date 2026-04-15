package com.ai.challenge.sharedkernel.identity

import java.util.UUID

/**
 * Typed identifier for aggregate User.
 *
 * Value class over String (UUID). Ensures type safety --
 * impossible to accidentally pass [AgentSessionId] or [ProjectId]
 * where [UserId] is expected.
 *
 * Generation: [generate] creates a new unique identifier.
 */
@JvmInline
value class UserId(val value: String) {
    companion object {
        fun generate(): UserId = UserId(value = UUID.randomUUID().toString())
    }
}
