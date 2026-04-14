package com.ai.challenge.conversation.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.conversation.model.Project
import com.ai.challenge.conversation.model.ProjectName
import com.ai.challenge.conversation.service.ProjectService
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.vo.SystemInstructions

/**
 * Application Service -- create project use case.
 *
 * Orchestrates project creation via [ProjectService].
 */
class CreateProjectUseCase(
    private val projectService: ProjectService,
) {
    suspend fun execute(
        name: ProjectName,
        systemInstructions: SystemInstructions,
    ): Either<DomainError, Project> = either {
        projectService.create(name = name, systemInstructions = systemInstructions).bind()
    }
}
