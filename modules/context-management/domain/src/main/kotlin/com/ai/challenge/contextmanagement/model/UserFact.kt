package com.ai.challenge.contextmanagement.model

import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Entity -- an LLM-extracted fact stored in user-scoped memory.
 *
 * Belongs to the user memory sub-domain of the Context Management BC.
 * Models a structured piece of information automatically derived from
 * conversations and attributed to a specific user (e.g. preferred language,
 * occupation, recurring topics).
 *
 * Invariants:
 * - Composite key: ([userId], [category], [key]) uniquely identifies a fact per user.
 * - [value] represents the current known value; it is replaced on update.
 */
data class UserFact(
    val userId: UserId,
    val category: FactCategory,
    val key: FactKey,
    val value: FactValue,
)
