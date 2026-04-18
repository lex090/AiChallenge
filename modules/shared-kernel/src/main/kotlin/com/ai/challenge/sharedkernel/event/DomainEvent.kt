package com.ai.challenge.sharedkernel.event

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.ai.challenge.sharedkernel.identity.UserId
import com.ai.challenge.sharedkernel.vo.SystemInstructions
import com.ai.challenge.sharedkernel.vo.TurnSnapshot

/**
 * Domain Event -- fact of a change that occurred in the domain.
 *
 * Events are immutable and represent a completed action (past tense).
 * Used for communication between Bounded Contexts
 * (Conversation -> Context Management) without creating
 * direct dependencies.
 *
 * Each event variant carries only the identifiers relevant to that event.
 * Session-scoped events carry [AgentSessionId]; project-scoped events
 * carry [ProjectId]. There is no forced common field because future
 * event types (e.g. user-scoped) may not involve sessions at all.
 */
sealed interface DomainEvent {

    /**
     * Domain Event -- a new Turn was recorded in a session.
     *
     * Published from ChatService after successful Turn save.
     * Carries [TurnSnapshot] (read-only projection) instead of the full
     * Turn entity to avoid leaking Conversation aggregate internals.
     *
     * Subscribers:
     * - Context Management: may trigger incremental fact extraction
     *   or summary update depending on active strategy.
     */
    data class TurnRecorded(
        val sessionId: AgentSessionId,
        val turnSnapshot: TurnSnapshot,
        val branchId: BranchId,
    ) : DomainEvent

    /**
     * Domain Event -- a new AgentSession was created.
     *
     * Published from CreateSessionUseCase after session + main branch creation.
     *
     * Subscribers: none currently (extensibility point).
     */
    data class SessionCreated(
        val sessionId: AgentSessionId,
    ) : DomainEvent

    /**
     * Domain Event -- an AgentSession was deleted.
     *
     * Published from DeleteSessionUseCase after aggregate deletion.
     *
     * Subscribers:
     * - Context Management: SessionDeletedCleanupHandler deletes
     *   orphaned Facts and Summaries for this session.
     */
    data class SessionDeleted(
        val sessionId: AgentSessionId,
    ) : DomainEvent

    /**
     * Domain Event -- a Project was deleted.
     *
     * Published from DeleteProjectUseCase after project deletion.
     * All sessions belonging to the project become free (projectId = null).
     *
     * Subscribers:
     * - Context Management: ProjectDeletedCleanupHandler cleans up project memory.
     */
    data class ProjectDeleted(
        val projectId: ProjectId,
    ) : DomainEvent

    /**
     * Domain Event -- project instructions were created or updated.
     *
     * Published from CreateProjectUseCase and UpdateProjectUseCase
     * after successful project save.
     *
     * Subscribers:
     * - Context Management: ProjectInstructionsChangedHandler upserts
     *   instructions in project-scoped memory.
     */
    data class ProjectInstructionsChanged(
        val projectId: ProjectId,
        val instructions: SystemInstructions,
    ) : DomainEvent

    /**
     * Domain Event -- a new User was created.
     *
     * Published from CreateUserUseCase after successful user save.
     * Carries [userName] as a plain String because shared-kernel must not
     * depend on Conversation's domain value objects.
     *
     * Subscribers:
     * - Context Management: may initialise user-scoped memory.
     */
    data class UserCreated(
        val userId: UserId,
        val userName: String,
    ) : DomainEvent

    /**
     * Domain Event -- a User's profile was updated.
     *
     * Published from UpdateUserUseCase after successful save.
     *
     * Subscribers: none currently (extensibility point).
     */
    data class UserUpdated(
        val userId: UserId,
    ) : DomainEvent

    /**
     * Domain Event -- a User was deleted.
     *
     * Published from DeleteUserUseCase after aggregate deletion.
     *
     * Subscribers:
     * - Context Management: cleanup handler removes user-scoped memory.
     */
    data class UserDeleted(
        val userId: UserId,
    ) : DomainEvent
}
