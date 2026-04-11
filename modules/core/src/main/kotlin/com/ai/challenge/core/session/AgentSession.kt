package com.ai.challenge.core.session

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.shared.UpdatedAt
import kotlin.time.Clock

/**
 * Aggregate Root — central domain entity.
 *
 * AgentSession represents one conversation between user and AI agent.
 * Single entry point for all operations within a session.
 *
 * Invariants protected by this aggregate:
 * - Exactly one branch is active ([activeBranchId])
 * - Main branch always exists and cannot be deleted
 * - All child entities (Branch, Turn) exist only in session context
 */
data class AgentSession(
    val id: AgentSessionId,
    val title: SessionTitle,
    val contextManagementType: ContextManagementType,
    val activeBranchId: BranchId,
    val createdAt: CreatedAt,
    val updatedAt: UpdatedAt,
) {
    fun withUpdatedTitle(newTitle: SessionTitle): AgentSession =
        copy(title = newTitle, updatedAt = UpdatedAt(value = Clock.System.now()))

    fun withContextManagementType(type: ContextManagementType): AgentSession =
        copy(contextManagementType = type, updatedAt = UpdatedAt(value = Clock.System.now()))

    fun withActiveBranch(branchId: BranchId): AgentSession =
        copy(activeBranchId = branchId, updatedAt = UpdatedAt(value = Clock.System.now()))
}
