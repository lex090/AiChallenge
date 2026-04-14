package com.ai.challenge.conversation.repository

import com.ai.challenge.conversation.model.Project
import com.ai.challenge.sharedkernel.identity.ProjectId

/**
 * Repository -- sole access point to the [Project] aggregate persistence.
 *
 * DDD rule: one repository per aggregate. Project is a separate aggregate
 * from AgentSession, so it has its own repository.
 */
interface ProjectRepository {
    suspend fun save(project: Project): Project
    suspend fun get(id: ProjectId): Project?
    suspend fun delete(id: ProjectId)
    suspend fun list(): List<Project>
    suspend fun update(project: Project): Project
}
