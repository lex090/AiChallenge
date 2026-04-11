package com.ai.challenge.core.fact

import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.session.AgentSessionId

/**
 * Value Object — extracted fact from conversation.
 *
 * Has no identity — facts are fully recreated on each message
 * (replace-all semantics in [FactRepository]).
 * Defined only by [category] + [key] + [value].
 *
 * Not part of [AgentSession] aggregate because:
 * - AgentSession doesn't know about facts
 * - Facts are internal state of StickyFacts strategy
 * - Different lifecycle (replace-all vs append-only for turns)
 * - Stored in separate database (facts.db)
 *
 * [sessionId] is a correlation ID, not aggregate membership.
 */
data class Fact(
    val sessionId: AgentSessionId,
    val category: FactCategory,
    val key: FactKey,
    val value: FactValue,
)
