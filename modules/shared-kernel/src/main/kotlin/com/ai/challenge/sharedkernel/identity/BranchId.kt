package com.ai.challenge.sharedkernel.identity

import java.util.UUID

/**
 * Typed identifier for entity Branch.
 *
 * Value class over String (UUID). Ensures type safety --
 * impossible to accidentally pass [AgentSessionId] or [TurnId]
 * where [BranchId] is expected.
 *
 * Generation: [generate] creates a new unique identifier.
 */
@JvmInline
value class BranchId(val value: String) {
    companion object {
        fun generate(): BranchId = BranchId(value = UUID.randomUUID().toString())
    }
}
