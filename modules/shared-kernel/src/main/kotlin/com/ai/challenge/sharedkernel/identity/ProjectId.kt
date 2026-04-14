package com.ai.challenge.sharedkernel.identity

import java.util.UUID

/**
 * Typed identifier for aggregate Project.
 *
 * Value class over String (UUID). Ensures type safety --
 * impossible to accidentally pass [AgentSessionId] or [BranchId]
 * where [ProjectId] is expected.
 *
 * Generation: [generate] creates a new unique identifier.
 */
@JvmInline
value class ProjectId(val value: String) {
    companion object {
        fun generate(): ProjectId = ProjectId(value = UUID.randomUUID().toString())
    }
}
