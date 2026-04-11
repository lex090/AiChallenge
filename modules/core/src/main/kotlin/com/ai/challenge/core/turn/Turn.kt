package com.ai.challenge.core.turn

import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.usage.model.UsageRecord

/**
 * Child Entity of AgentSession aggregate. Immutable.
 *
 * [usage] — embedded value object with metrics (tokens + cost).
 */
data class Turn(
    val id: TurnId,
    val sessionId: AgentSessionId,
    val userMessage: MessageContent,
    val assistantMessage: MessageContent,
    val usage: UsageRecord,
    val createdAt: CreatedAt,
)
