package com.ai.challenge.conversation.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.conversation.model.AgentSession
import com.ai.challenge.conversation.model.SessionTitle
import com.ai.challenge.conversation.service.SessionService
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.event.DomainEvent
import com.ai.challenge.sharedkernel.event.DomainEventPublisher

/**
 * Application Service -- create session use case.
 *
 * Orchestrates:
 * 1. Creates [AgentSession] via [SessionService] (includes main Branch creation)
 * 2. Publishes [DomainEvent.SessionCreated] event
 */
class CreateSessionUseCase(
    private val sessionService: SessionService,
    private val eventPublisher: DomainEventPublisher,
) {
    suspend fun execute(title: SessionTitle): Either<DomainError, AgentSession> = either {
        val session = sessionService.create(title = title).bind()
        eventPublisher.publish(event = DomainEvent.SessionCreated(sessionId = session.id))
        session
    }
}
