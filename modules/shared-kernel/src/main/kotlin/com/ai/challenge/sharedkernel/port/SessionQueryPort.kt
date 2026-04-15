package com.ai.challenge.sharedkernel.port

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Port -- read-only access to session ownership data for cross-context queries.
 *
 * Defined in shared kernel so that external bounded contexts can resolve the
 * [UserId] associated with an [AgentSessionId] without depending on
 * Conversation's aggregate internals.
 *
 * Returns null when no session exists for the given [AgentSessionId].
 *
 * Dependency direction: defined in shared-kernel,
 * implemented in conversation/data, consumed by other bounded contexts.
 */
interface SessionQueryPort {
    suspend fun getUserId(sessionId: AgentSessionId): UserId?
}
