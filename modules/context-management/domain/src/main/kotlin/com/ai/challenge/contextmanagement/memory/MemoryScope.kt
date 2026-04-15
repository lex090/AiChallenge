package com.ai.challenge.contextmanagement.memory

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Value Object -- scope of agent memory, defines the storage boundary.
 *
 * Each scope variant determines which memories are accessible.
 * Three scopes are supported: Session (per conversation), Project (per project),
 * and User (cross-session, cross-project, tied to a specific user identity).
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
    /** Memory bound to a specific user, spanning all sessions and projects. */
    data class User(val userId: UserId) : MemoryScope
}
