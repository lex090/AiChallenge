package com.ai.challenge.core.branch

import com.ai.challenge.core.chat.model.BranchName
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.turn.TurnId

/**
 * Child Entity of AgentSession aggregate.
 *
 * [parentId] — reference to parent branch. null means main branch.
 * [turnIds] — ordered list of references to turns in this branch.
 */
data class Branch(
    val id: BranchId,
    val sessionId: AgentSessionId,
    val parentId: BranchId?,
    val name: BranchName,
    val turnIds: List<TurnId>,
    val createdAt: CreatedAt,
) {
    val isMain: Boolean get() = parentId == null
}
