package com.ai.challenge.core

data class BranchTree(
    val sessionId: SessionId,
    val checkpoints: List<CheckpointNode>,
)

data class CheckpointNode(
    val turnIndex: Int,
    val branches: List<BranchNode>,
)

data class BranchNode(
    val branch: Branch,
    val turnCount: Int,
    val isActive: Boolean,
)
