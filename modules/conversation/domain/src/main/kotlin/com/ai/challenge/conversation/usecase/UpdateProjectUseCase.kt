package com.ai.challenge.conversation.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.conversation.model.Project
import com.ai.challenge.conversation.model.ProjectName
import com.ai.challenge.conversation.service.ProjectService
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.ai.challenge.sharedkernel.vo.SystemInstructions

/**
 * Application Service -- update project use case.
 *
 * Orchestrates project name and instructions update via [ProjectService].
 */
class UpdateProjectUseCase(
    private val projectService: ProjectService,
) {
    suspend fun execute(
        id: ProjectId,
        name: ProjectName,
        systemInstructions: SystemInstructions,
    ): Either<DomainError, Project> = either {
        projectService.update(id = id, name = name, systemInstructions = systemInstructions).bind()
    }
}
