package com.ai.challenge.contextmanagement.model

import com.ai.challenge.sharedkernel.identity.ProjectId

/**
 * Value Object -- project-level instructions stored in CM memory.
 *
 * Synchronized from Conversation BC via [DomainEvent.ProjectInstructionsChanged].
 * Has no identity -- defined by [projectId] + [content].
 *
 * Not part of any aggregate -- internal state of Context Management BC.
 * [projectId] is a correlation ID, not aggregate membership.
 *
 * Invariants:
 * - [content] is never blank.
 * - Immutable after creation.
 */
data class ProjectInstructions(
    val projectId: ProjectId,
    val content: InstructionsContent,
)
