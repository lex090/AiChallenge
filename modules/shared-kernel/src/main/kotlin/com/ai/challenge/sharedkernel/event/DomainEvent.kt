package com.ai.challenge.sharedkernel.event

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.vo.TurnSnapshot

/**
 * Domain Event -- fact of a change that occurred in the domain.
 *
 * Events are immutable and represent a completed action (past tense).
 * Used for communication between Bounded Contexts
 * (Conversation -> Context Management) without creating
 * direct dependencies.
 *
 * All events contain [sessionId] as correlation identifier,
 * allowing Context Management context to identify affected data.
 */
sealed interface DomainEvent {

    val sessionId: AgentSessionId

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
        override val sessionId: AgentSessionId,
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
        override val sessionId: AgentSessionId,
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
        override val sessionId: AgentSessionId,
    ) : DomainEvent
}
