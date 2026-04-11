package com.ai.challenge.core.branch

import com.ai.challenge.core.session.AgentSessionId
import kotlin.time.Instant

data class Branch(
    val id: BranchId,
    val sessionId: AgentSessionId,
    val name: String,
    val parentBranchId: BranchId?,
    val isActive: Boolean,
    val createdAt: Instant,
) {
    val isMain: Boolean get() = parentBranchId == null
}
