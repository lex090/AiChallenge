package com.ai.challenge.contextmanagement.repository

import com.ai.challenge.contextmanagement.model.ProjectInstructions
import com.ai.challenge.sharedkernel.identity.ProjectId

/**
 * Repository -- persistence for [ProjectInstructions] value objects
 * in Context Management bounded context.
 *
 * Internal state of CM memory system. Accessed only by
 * [ProjectInstructionsMemoryProvider].
 *
 * [save] implements upsert semantics: inserts or replaces
 * instructions for the given project.
 *
 * Invariants:
 * - [save] is atomic: one row per project.
 * - [getByProject] returns instructions or null if not yet synced.
 */
interface ProjectInstructionsRepository {
    suspend fun save(instructions: ProjectInstructions)
    suspend fun getByProject(projectId: ProjectId): ProjectInstructions?
    suspend fun deleteByProject(projectId: ProjectId)
}
