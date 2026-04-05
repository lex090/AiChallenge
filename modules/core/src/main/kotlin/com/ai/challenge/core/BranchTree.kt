package com.ai.challenge.core

data class CheckpointNode(
    val turnIndex: Int,
    val branches: List<BranchNode>,
)

data class BranchNode(
    val branch: Branch,
    val turnCount: Int,
)

data class BranchTree(
    val sessionId: SessionId,
    val mainTurnCount: Int,
    val checkpoints: List<CheckpointNode>,
)
