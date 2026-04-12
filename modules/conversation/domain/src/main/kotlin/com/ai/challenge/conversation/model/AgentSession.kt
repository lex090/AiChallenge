package com.ai.challenge.conversation.model

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.vo.ContextModeId
import com.ai.challenge.sharedkernel.vo.CreatedAt
import com.ai.challenge.sharedkernel.vo.UpdatedAt
import kotlin.time.Clock

/**
 * Aggregate Root -- root of the "Conversation" aggregate.
 *
 * Represents one conversation between user and AI agent.
 * Single entry point for all operations within a session:
 * creating branches, appending turns.
 *
 * Transactional boundary: all changes to [Branch] and [Turn]
 * go through this aggregate and are saved atomically.
 *
 * Invariants:
 * - Always has exactly one main [Branch] ([Branch.sourceTurnId] == null)
 * - [contextModeId] references a valid context mode (validated externally via [ContextModeValidatorPort])
 * - Main [Branch] cannot be deleted
 * - [title] is auto-generated from first message if empty
 *
 * Child entities: [Branch], [Turn]
 */
data class AgentSession(
    val id: AgentSessionId,
    val title: SessionTitle,
    val contextModeId: ContextModeId,
    val createdAt: CreatedAt,
    val updatedAt: UpdatedAt,
) {
    fun withUpdatedTitle(newTitle: SessionTitle): AgentSession =
        copy(title = newTitle, updatedAt = UpdatedAt(value = Clock.System.now()))

    fun withContextModeId(contextModeId: ContextModeId): AgentSession =
        copy(contextModeId = contextModeId, updatedAt = UpdatedAt(value = Clock.System.now()))

    fun ensureBranchDeletable(branch: Branch): Either<DomainError, Unit> = either {
        ensure(!branch.isMain) {
            DomainError.MainBranchCannotBeDeleted(sessionId = id)
        }
    }
}
