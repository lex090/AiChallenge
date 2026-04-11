package com.ai.challenge.core.event

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn

sealed interface DomainEvent {

    data class TurnRecorded(
        val sessionId: AgentSessionId,
        val turn: Turn,
    ) : DomainEvent

    data class SessionCreated(
        val sessionId: AgentSessionId,
    ) : DomainEvent

    data class SessionDeleted(
        val sessionId: AgentSessionId,
    ) : DomainEvent
}
