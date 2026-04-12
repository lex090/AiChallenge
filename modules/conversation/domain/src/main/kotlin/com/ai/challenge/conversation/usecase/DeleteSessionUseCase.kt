package com.ai.challenge.conversation.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.conversation.service.SessionService
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.event.DomainEvent
import com.ai.challenge.sharedkernel.event.DomainEventPublisher
import com.ai.challenge.sharedkernel.identity.AgentSessionId

/**
 * Application Service -- delete session use case.
 *
 * Orchestrates:
 * 1. Deletes aggregate via [SessionService]
 * 2. Publishes [DomainEvent.SessionDeleted] event
 *    -> Context Management cleans up Facts and Summaries
 *
 * Does NOT contain "always one session" policy --
 * that is [ApplicationInitService] responsibility.
 */
class DeleteSessionUseCase(
    private val sessionService: SessionService,
    private val eventPublisher: DomainEventPublisher,
) {
    suspend fun execute(sessionId: AgentSessionId): Either<DomainError, Unit> = either {
        sessionService.delete(id = sessionId).bind()
        eventPublisher.publish(event = DomainEvent.SessionDeleted(sessionId = sessionId))
    }
}
