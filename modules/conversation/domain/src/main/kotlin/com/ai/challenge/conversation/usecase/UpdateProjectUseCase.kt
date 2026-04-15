package com.ai.challenge.conversation.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.conversation.model.Project
import com.ai.challenge.conversation.model.ProjectName
import com.ai.challenge.conversation.service.ProjectService
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.event.DomainEvent
import com.ai.challenge.sharedkernel.event.DomainEventPublisher
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.ai.challenge.sharedkernel.vo.SystemInstructions

/**
 * Application Service -- update project use case.
 *
 * Orchestrates project name and instructions update via [ProjectService]
 * and publishes [DomainEvent.ProjectInstructionsChanged] for CM memory sync.
 */
class UpdateProjectUseCase(
    private val projectService: ProjectService,
    private val eventPublisher: DomainEventPublisher,
) {
    suspend fun execute(
        id: ProjectId,
        name: ProjectName,
        systemInstructions: SystemInstructions,
    ): Either<DomainError, Project> = either {
        val project = projectService.update(id = id, name = name, systemInstructions = systemInstructions).bind()
        eventPublisher.publish(
            event = DomainEvent.ProjectInstructionsChanged(
                projectId = project.id,
                instructions = project.systemInstructions,
            ),
        )
        project
    }
}
