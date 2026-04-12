package com.ai.challenge.contextmanagement.model

import com.ai.challenge.sharedkernel.identity.AgentSessionId

/**
 * Value Object -- extracted fact from conversation.
 *
 * Has no identity -- facts are fully recreated on each message
 * (replace-all semantics in [FactRepository]).
 * Defined only by [category] + [key] + [value].
 *
 * Not part of AgentSession aggregate because:
 * - AgentSession doesn't know about facts
 * - Facts are internal state of StickyFacts strategy
 * - Different lifecycle (replace-all vs append-only for turns)
 *
 * [sessionId] is a correlation ID, not aggregate membership.
 *
 * Invariants:
 * - [key] and [value] are never blank.
 * - [category] classifies the fact for structured display.
 */
data class Fact(
    val sessionId: AgentSessionId,
    val category: FactCategory,
    val key: FactKey,
    val value: FactValue,
)
