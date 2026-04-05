package com.ai.challenge.core

data class ContextState(
    val sessionId: SessionId,
    val history: List<Turn>,
    val newMessage: String,
    val messages: List<ContextMessage> = emptyList(),
    val facts: List<Fact> = emptyList(),
    val activeBranchId: BranchId? = null,
)
