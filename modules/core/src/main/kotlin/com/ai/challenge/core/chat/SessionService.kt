package com.ai.challenge.core.chat

import arrow.core.Either
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId

/**
 * Domain Service — [AgentSession] lifecycle management.
 *
 * CRUD operations on sessions. Updates title and
 * [ContextManagementType]. Does not manage branches or turns
 * directly — that is [BranchService] and [ChatService] responsibility.
 *
 * Contains no own state — all logic is stateless.
 */
interface SessionService {
    suspend fun create(title: SessionTitle): Either<DomainError, AgentSession>
    suspend fun get(id: AgentSessionId): Either<DomainError, AgentSession>
    suspend fun delete(id: AgentSessionId): Either<DomainError, Unit>
    suspend fun list(): Either<DomainError, List<AgentSession>>
    suspend fun updateTitle(id: AgentSessionId, title: SessionTitle): Either<DomainError, AgentSession>
    suspend fun updateContextManagementType(id: AgentSessionId, type: ContextManagementType): Either<DomainError, AgentSession>
}
