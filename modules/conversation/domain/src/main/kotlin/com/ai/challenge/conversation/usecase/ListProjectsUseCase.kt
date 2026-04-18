package com.ai.challenge.conversation.usecase

import arrow.core.Either
import com.ai.challenge.conversation.model.Project
import com.ai.challenge.conversation.service.ProjectService
import com.ai.challenge.sharedkernel.error.DomainError

/**
 * Application Service -- list projects use case.
 *
 * Returns all projects for UI display.
 */
class ListProjectsUseCase(
    private val projectService: ProjectService,
) {
    suspend fun execute(): Either<DomainError, List<Project>> =
        projectService.list()
}
