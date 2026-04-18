package com.ai.challenge.contextmanagement.model

import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Entity -- user-level memory storing free-form instructions and preferences.
 *
 * Belongs to the User aggregate boundary within the Context Management BC.
 * Models the persistent, cross-session knowledge that an LLM should apply
 * whenever interacting with a particular user (e.g. language preference,
 * communication style, recurring constraints).
 *
 * Invariants:
 * - One record per [userId]; uniqueness enforced by the repository.
 * - [content] must not be blank when saved.
 */
data class UserPreferencesMemory(
    val userId: UserId,
    val content: InstructionsContent,
)
