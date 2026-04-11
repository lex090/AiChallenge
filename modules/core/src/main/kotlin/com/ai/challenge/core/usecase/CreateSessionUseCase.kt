package com.ai.challenge.core.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.event.DomainEvent
import com.ai.challenge.core.event.DomainEventPublisher
import com.ai.challenge.core.session.AgentSession

/**
 * Application Service — create session use case.
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
