package com.ai.challenge.conversation.service

import arrow.core.Either
import com.ai.challenge.conversation.model.AgentSession
import com.ai.challenge.conversation.model.SessionTitle
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.vo.ContextModeId

/**
 * Domain Service -- [AgentSession] lifecycle management.
 *
 * CRUD operations on sessions. Updates title and
 * [ContextModeId]. Does not manage branches or turns
 * directly -- that is [BranchService] and [ChatService] responsibility.
 *
 * Contains no own state -- all logic is stateless.
 */
interface SessionService {
    suspend fun create(title: SessionTitle): Either<DomainError, AgentSession>
    suspend fun get(id: AgentSessionId): Either<DomainError, AgentSession>
    suspend fun delete(id: AgentSessionId): Either<DomainError, Unit>
    suspend fun list(): Either<DomainError, List<AgentSession>>
    suspend fun updateTitle(id: AgentSessionId, title: SessionTitle): Either<DomainError, AgentSession>
    suspend fun updateContextModeId(id: AgentSessionId, contextModeId: ContextModeId): Either<DomainError, AgentSession>
}
