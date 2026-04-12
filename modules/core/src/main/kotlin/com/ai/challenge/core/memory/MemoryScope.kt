package com.ai.challenge.core.memory

import com.ai.challenge.core.session.AgentSessionId

/**
 * Scope of agent memory — defines the storage boundary.
 * Value Object (E3): immutable, equality by attributes.
 *
 * Each scope variant determines which memories are accessible.
 * Extensible: add new variants for user-level, branch-level memory.
 */
sealed interface MemoryScope {
    /** Memory bound to a specific agent session. */
    data class Session(val sessionId: AgentSessionId) : MemoryScope
}
