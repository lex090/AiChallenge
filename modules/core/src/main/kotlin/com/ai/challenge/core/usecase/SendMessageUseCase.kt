package com.ai.challenge.core.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.ChatService
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.event.DomainEvent
import com.ai.challenge.core.event.DomainEventPublisher
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn

/**
 * Application Service — send message use case.
 *
 * Orchestrates:
 * 1. Delegates to [ChatService] for context preparation, LLM call, and Turn save
 * 2. Publishes [DomainEvent.TurnRecorded] event for Context Management context
 * 3. Auto-generates session title from first message (if empty)
 *
 * Presentation layer calls this use case instead of [ChatService] directly.
 */
class SendMessageUseCase(
    private val chatService: ChatService,
    private val sessionService: SessionService,
    private val eventPublisher: DomainEventPublisher,
) {
    suspend fun execute(
        sessionId: AgentSessionId,
        branchId: BranchId,
        message: MessageContent,
    ): Either<DomainError, Turn> = either {
        val turn = chatService.send(sessionId = sessionId, branchId = branchId, message = message).bind()

        eventPublisher.publish(event = DomainEvent.TurnRecorded(sessionId = sessionId, turn = turn, branchId = branchId))

        val session = sessionService.get(id = sessionId).bind()
        if (session.title.value.isEmpty()) {
            sessionService.updateTitle(id = sessionId, title = SessionTitle(value = message.value.take(n = 50)))
        }

        turn
    }
}
