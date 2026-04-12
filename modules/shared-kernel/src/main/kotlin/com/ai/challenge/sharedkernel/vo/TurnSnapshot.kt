package com.ai.challenge.sharedkernel.vo

import com.ai.challenge.sharedkernel.identity.TurnId

/**
 * Value Object -- read-only projection of a Turn for cross-context use.
 *
 * Carries the essential Turn data needed by Context Management bounded context
 * (via domain events and [TurnQueryPort]) without exposing the full Turn entity
 * or creating a dependency on Conversation's aggregate internals.
 *
 * Contains only the fields that Context Management needs:
 * identity and message content.
 * Excludes usage metrics, session reference, and timestamps
 * which are Conversation-internal.
 *
 * Invariants:
 * - Immutable projection -- never modified after creation.
 * - [userMessage] and [assistantMessage] are never blank.
 */
data class TurnSnapshot(
    val turnId: TurnId,
    val userMessage: MessageContent,
    val assistantMessage: MessageContent,
)
