package com.ai.challenge.conversation.service

import arrow.core.Either
import com.ai.challenge.conversation.model.Turn
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.ai.challenge.sharedkernel.identity.UserId
import com.ai.challenge.sharedkernel.vo.MessageContent

/**
 * Domain Service -- sending messages to AI agent.
 *
 * Orchestrates: context preparation, LLM call,
 * [Turn] creation and persistence.
 *
 * Contains no own state -- all logic is stateless.
 *
 * @param userId optional user identity; passed to context preparation so that
 * user-level memory (preferences, facts, notes) is included in the context.
 */
interface ChatService {
    suspend fun send(
        sessionId: AgentSessionId,
        branchId: BranchId,
        message: MessageContent,
        projectId: ProjectId?,
        userId: UserId?,
    ): Either<DomainError, Turn>
}
