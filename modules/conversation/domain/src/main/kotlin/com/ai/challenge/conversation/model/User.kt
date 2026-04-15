package com.ai.challenge.conversation.model

import com.ai.challenge.sharedkernel.identity.UserId
import com.ai.challenge.sharedkernel.vo.CreatedAt
import com.ai.challenge.sharedkernel.vo.UpdatedAt
import kotlin.time.Clock

/**
 * Aggregate Root -- represents a user of the application.
 *
 * A User is an organizational entity that may own sessions and have
 * associated user-scoped memory (facts, summaries, preferences).
 * The [preferences] field stores free-form user configuration that
 * can be injected into LLM context.
 *
 * Invariants:
 * - [name] must not be blank
 * - [preferences] may be empty (user has not configured preferences yet)
 * - User can exist without sessions (empty user)
 * - Deleting a user does not delete sessions -- they become free (userId = null)
 */
data class User(
    val id: UserId,
    val name: UserName,
    val preferences: UserPreferences,
    val createdAt: CreatedAt,
    val updatedAt: UpdatedAt,
) {
    fun withUpdatedName(newName: UserName): User =
        copy(name = newName, updatedAt = UpdatedAt(value = Clock.System.now()))

    fun withUpdatedPreferences(newPreferences: UserPreferences): User =
        copy(preferences = newPreferences, updatedAt = UpdatedAt(value = Clock.System.now()))
}
