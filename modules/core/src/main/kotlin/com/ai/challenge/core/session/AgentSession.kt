package com.ai.challenge.core.session

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.shared.UpdatedAt
import kotlin.time.Clock

/**
 * Aggregate Root — root of the "Conversation" aggregate.
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
 * - [Branch] can only be created if [contextManagementType] == [ContextManagementType.Branching]
 * - Main [Branch] cannot be deleted
 * - [title] is auto-generated from first message if empty
 *
 * Child entities: [Branch], [Turn]
 */
data class AgentSession(
    val id: AgentSessionId,
    val title: SessionTitle,
    val contextManagementType: ContextManagementType,
    val createdAt: CreatedAt,
    val updatedAt: UpdatedAt,
) {
    fun withUpdatedTitle(newTitle: SessionTitle): AgentSession =
        copy(title = newTitle, updatedAt = UpdatedAt(value = Clock.System.now()))

    fun withContextManagementType(type: ContextManagementType): AgentSession =
        copy(contextManagementType = type, updatedAt = UpdatedAt(value = Clock.System.now()))

    fun ensureBranchDeletable(branch: Branch): Either<DomainError, Unit> = either {
        ensure(!branch.isMain) {
            DomainError.MainBranchCannotBeDeleted(sessionId = id)
        }
    }
}
