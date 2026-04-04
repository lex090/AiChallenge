package com.ai.challenge.session

import kotlin.time.Clock
import kotlin.time.Instant

data class AgentSession(
    val id: SessionId,
    val title: String = "",
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val history: List<Turn> = emptyList(),
) {
    fun addTurn(turn: Turn): AgentSession =
        copy(history = history + turn, updatedAt = Clock.System.now())
}
