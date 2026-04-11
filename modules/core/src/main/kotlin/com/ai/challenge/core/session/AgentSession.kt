package com.ai.challenge.core.session

import kotlin.time.Clock
import kotlin.time.Instant

data class AgentSession(
    val id: AgentSessionId,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun withUpdatedTitle(newTitle: String): AgentSession =
        copy(title = newTitle, updatedAt = Clock.System.now())

    companion object {
        fun create(title: String): AgentSession {
            val now = Clock.System.now()
            return AgentSession(
                id = AgentSessionId.generate(),
                title = title,
                createdAt = now,
                updatedAt = now,
            )
        }
    }
}
