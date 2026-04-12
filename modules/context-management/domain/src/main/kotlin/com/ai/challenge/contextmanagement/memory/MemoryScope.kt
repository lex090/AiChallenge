package com.ai.challenge.contextmanagement.memory

import com.ai.challenge.sharedkernel.identity.AgentSessionId

/**
 * Value Object -- scope of agent memory, defines the storage boundary.
 *
 * Each scope variant determines which memories are accessible.
 * Extensible: add new variants for user-level, branch-level memory.
 *
 * Invariants:
 * - Immutable after creation.
 * - Equality by attributes.
 */
sealed interface MemoryScope {
    /** Memory bound to a specific agent session. */
    data class Session(val sessionId: AgentSessionId) : MemoryScope
}
