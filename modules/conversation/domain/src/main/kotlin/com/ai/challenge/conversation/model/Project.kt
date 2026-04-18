package com.ai.challenge.conversation.model

import com.ai.challenge.sharedkernel.identity.ProjectId
import com.ai.challenge.sharedkernel.vo.CreatedAt
import com.ai.challenge.sharedkernel.vo.SystemInstructions
import com.ai.challenge.sharedkernel.vo.UpdatedAt
import kotlin.time.Clock

/**
 * Aggregate Root -- represents a project that groups sessions
 * and provides system-level instructions for LLM communication.
 *
 * Projects are an organizational and contextual unit: each project
 * has a name and system instructions that are prepended as a system
 * message to every LLM request for sessions belonging to this project.
 *
 * Invariants:
 * - [name] must not be blank
 * - Project can exist without sessions (empty project)
 * - Deleting a project does not delete sessions -- they become free (projectId = null)
 */
data class Project(
    val id: ProjectId,
    val name: ProjectName,
    val systemInstructions: SystemInstructions,
    val createdAt: CreatedAt,
    val updatedAt: UpdatedAt,
) {
    fun withUpdatedName(newName: ProjectName): Project =
        copy(name = newName, updatedAt = UpdatedAt(value = Clock.System.now()))

    fun withUpdatedInstructions(newInstructions: SystemInstructions): Project =
        copy(systemInstructions = newInstructions, updatedAt = UpdatedAt(value = Clock.System.now()))
}
