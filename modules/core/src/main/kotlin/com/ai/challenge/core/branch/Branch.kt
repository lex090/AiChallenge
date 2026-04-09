package com.ai.challenge.core.branch

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import kotlin.time.Instant

data class Branch(
    val id: BranchId,
    val sessionId: AgentSessionId,
    val name: String,
    val parentTurnId: TurnId?,
    val parentBranchId: BranchId?,
    val isActive: Boolean,
    val createdAt: Instant,
) {
    val isMain: Boolean get() = parentTurnId == null
}
