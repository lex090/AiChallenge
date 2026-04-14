package com.ai.challenge.conversation.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.conversation.service.ProjectService
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.event.DomainEvent
import com.ai.challenge.sharedkernel.event.DomainEventPublisher
import com.ai.challenge.sharedkernel.identity.ProjectId

/**
 * Application Service -- delete project use case.
 *
 * Orchestrates:
 * 1. Deletes project via [ProjectService] (clears projectId from sessions)
 * 2. Publishes [DomainEvent.ProjectDeleted] event
 */
class DeleteProjectUseCase(
    private val projectService: ProjectService,
    private val eventPublisher: DomainEventPublisher,
) {
    suspend fun execute(projectId: ProjectId): Either<DomainError, Unit> = either {
        projectService.delete(id = projectId).bind()
        eventPublisher.publish(event = DomainEvent.ProjectDeleted(projectId = projectId))
    }
}
