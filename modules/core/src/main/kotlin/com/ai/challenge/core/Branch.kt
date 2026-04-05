package com.ai.challenge.core

import kotlin.time.Clock
import kotlin.time.Instant

data class Branch(
    val id: BranchId = BranchId.generate(),
    val sessionId: SessionId,
    val name: String,
    val checkpointTurnIndex: Int,
    val parentBranchId: BranchId? = null,
    val createdAt: Instant = Clock.System.now(),
)
