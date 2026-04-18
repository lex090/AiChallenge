package com.ai.challenge.conversation.service

import arrow.core.Either
import com.ai.challenge.conversation.model.Project
import com.ai.challenge.conversation.model.ProjectName
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.ai.challenge.sharedkernel.vo.SystemInstructions

/**
 * Domain Service -- [Project] lifecycle management.
 *
 * CRUD operations on projects. On deletion, clears projectId
 * from all associated sessions (they become free).
 *
 * Contains no own state -- all logic is stateless.
 */
interface ProjectService {
    suspend fun create(name: ProjectName, systemInstructions: SystemInstructions): Either<DomainError, Project>
    suspend fun get(id: ProjectId): Either<DomainError, Project>
    suspend fun delete(id: ProjectId): Either<DomainError, Unit>
    suspend fun list(): Either<DomainError, List<Project>>
    suspend fun update(id: ProjectId, name: ProjectName, systemInstructions: SystemInstructions): Either<DomainError, Project>
}
