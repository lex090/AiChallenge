package com.ai.challenge.contextmanagement.memory.impl

import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.ProjectInstructionsMemoryProvider
import com.ai.challenge.contextmanagement.model.ProjectInstructions
import com.ai.challenge.contextmanagement.repository.ProjectInstructionsRepository
import com.ai.challenge.sharedkernel.identity.ProjectId

/**
 * Default implementation of [ProjectInstructionsMemoryProvider].
 *
 * Delegates to [ProjectInstructionsRepository] for persistence.
 * Translates [MemoryScope] to [ProjectId].
 */
class DefaultProjectInstructionsMemoryProvider(
    private val projectInstructionsRepository: ProjectInstructionsRepository,
) : ProjectInstructionsMemoryProvider {

    override suspend fun get(scope: MemoryScope): ProjectInstructions? {
        val projectId = scope.toProjectId()
        return projectInstructionsRepository.getByProject(projectId = projectId)
    }

    override suspend fun save(scope: MemoryScope, instructions: ProjectInstructions) {
        projectInstructionsRepository.save(instructions = instructions)
    }

    override suspend fun clear(scope: MemoryScope) {
        when (scope) {
            is MemoryScope.Project -> projectInstructionsRepository.deleteByProject(projectId = scope.projectId)
            is MemoryScope.Session -> Unit
            is MemoryScope.User -> Unit
        }
    }

    private fun MemoryScope.toProjectId(): ProjectId = when (this) {
        is MemoryScope.Project -> projectId
        is MemoryScope.Session -> error("ProjectInstructionsMemoryProvider does not support Session scope")
        is MemoryScope.User -> error("ProjectInstructionsMemoryProvider does not support User scope")
    }
}
