package com.ai.challenge.conversation.impl

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.conversation.model.Project
import com.ai.challenge.conversation.model.ProjectName
import com.ai.challenge.conversation.repository.AgentSessionRepository
import com.ai.challenge.conversation.repository.ProjectRepository
import com.ai.challenge.conversation.service.ProjectService
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.ai.challenge.sharedkernel.vo.CreatedAt
import com.ai.challenge.sharedkernel.vo.SystemInstructions
import com.ai.challenge.sharedkernel.vo.UpdatedAt
import kotlin.time.Clock

/**
 * Domain Service implementation -- [Project] lifecycle management.
 *
 * Creates, updates, deletes projects. On deletion, clears
 * [AgentSession.projectId] for all sessions belonging to the project
 * via [AgentSessionRepository.clearProjectId].
 */
class AiProjectService(
    private val projectRepository: ProjectRepository,
    private val sessionRepository: AgentSessionRepository,
) : ProjectService {

    override suspend fun create(
        name: ProjectName,
        systemInstructions: SystemInstructions,
    ): Either<DomainError, Project> = either {
        val now = Clock.System.now()
        val project = Project(
            id = ProjectId.generate(),
            name = name,
            systemInstructions = systemInstructions,
            createdAt = CreatedAt(value = now),
            updatedAt = UpdatedAt(value = now),
        )
        projectRepository.save(project = project)
    }

    override suspend fun get(id: ProjectId): Either<DomainError, Project> = either {
        projectRepository.get(id = id)
            ?: raise(DomainError.ProjectNotFound(id = id))
    }

    override suspend fun delete(id: ProjectId): Either<DomainError, Unit> = either {
        projectRepository.get(id = id)
            ?: raise(DomainError.ProjectNotFound(id = id))
        sessionRepository.clearProjectId(projectId = id)
        projectRepository.delete(id = id)
    }

    override suspend fun list(): Either<DomainError, List<Project>> =
        Either.Right(value = projectRepository.list())

    override suspend fun update(
        id: ProjectId,
        name: ProjectName,
        systemInstructions: SystemInstructions,
    ): Either<DomainError, Project> = either {
        val project = projectRepository.get(id = id)
            ?: raise(DomainError.ProjectNotFound(id = id))
        val updated = project
            .withUpdatedName(newName = name)
            .withUpdatedInstructions(newInstructions = systemInstructions)
        projectRepository.update(project = updated)
    }
}
