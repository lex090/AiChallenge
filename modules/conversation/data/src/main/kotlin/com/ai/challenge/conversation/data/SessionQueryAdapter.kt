package com.ai.challenge.conversation.data

import com.ai.challenge.conversation.repository.AgentSessionRepository
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.UserId
import com.ai.challenge.sharedkernel.port.SessionQueryPort

/**
 * Adapter -- implements [SessionQueryPort] on top of [AgentSessionRepository].
 *
 * Bridges the Shared Kernel port with the Conversation BC repository,
 * allowing other bounded contexts to resolve the [UserId] for a given
 * [AgentSessionId] without depending on Conversation's aggregate internals.
 *
 * Returns null when the session does not exist or has no associated user.
 */
class SessionQueryAdapter(
    private val repository: AgentSessionRepository,
) : SessionQueryPort {
    override suspend fun getUserId(sessionId: AgentSessionId): UserId? {
        val session = repository.get(id = sessionId) ?: return null
        return session.userId
    }
}
