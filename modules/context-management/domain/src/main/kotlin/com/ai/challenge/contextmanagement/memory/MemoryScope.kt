package com.ai.challenge.contextmanagement.memory

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.ProjectId

/**
 * Value Object -- scope of agent memory, defines the storage boundary.
 *
 * Each scope variant determines which memories are accessible.
 *
 * Invariants:
 * - Immutable after creation.
 * - Equality by attributes.
 */
sealed interface MemoryScope {
    /** Memory bound to a specific agent session. */
    data class Session(val sessionId: AgentSessionId) : MemoryScope
    /** Memory bound to a specific project. */
    data class Project(val projectId: ProjectId) : MemoryScope
}
