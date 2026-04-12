package com.ai.challenge.sharedkernel.vo

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.TurnId

/**
 * Value Object -- read-only projection of a Turn for cross-context use.
 *
 * Carries the essential Turn data needed by Context Management bounded context
 * (via domain events and [TurnQueryPort]) without exposing the full Turn entity
 * or creating a dependency on Conversation's aggregate internals.
 *
 * Contains only the fields that Context Management needs:
 * identity, session reference, message content, and creation timestamp.
 * Excludes usage metrics (UsageRecord) which are Conversation-internal.
 *
 * Invariants:
 * - Immutable projection -- never modified after creation.
 * - [userMessage] and [assistantMessage] are never blank.
 */
data class TurnSnapshot(
    val id: TurnId,
    val sessionId: AgentSessionId,
    val userMessage: MessageContent,
    val assistantMessage: MessageContent,
    val createdAt: CreatedAt,
)
