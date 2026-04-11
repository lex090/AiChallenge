package com.ai.challenge.core.chat

import arrow.core.Either
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn

/**
 * Domain Service — sending messages to AI agent.
 *
 * Orchestrates: context preparation, LLM call,
 * [Turn] creation and persistence.
 *
 * Contains no own state — all logic is stateless.
 */
interface ChatService {
    suspend fun send(
        sessionId: AgentSessionId,
        branchId: BranchId,
        message: MessageContent,
    ): Either<DomainError, Turn>
}
