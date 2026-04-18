package com.ai.challenge.contextmanagement.repository

import com.ai.challenge.contextmanagement.model.UserPreferencesMemory
import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Repository port -- persistence contract for [UserPreferencesMemory].
 *
 * Defines the storage boundary for user-level instruction memory within the
 * Context Management BC. At most one preferences record exists per user.
 *
 * Invariants:
 * - [save] performs an upsert: an existing record for [UserPreferencesMemory.userId] is replaced.
 * - [getByUser] returns `null` when no preferences have been saved yet.
 * - [deleteByUser] is idempotent -- calling it for an unknown user is a no-op.
 */
interface UserPreferencesMemoryRepository {
    /** Upserts the preferences for the user identified by [UserPreferencesMemory.userId]. */
    suspend fun save(preferences: UserPreferencesMemory)
    /** Returns the preferences for [userId], or `null` if none exist. */
    suspend fun getByUser(userId: UserId): UserPreferencesMemory?
    /** Deletes all preferences for [userId]. No-op if none exist. */
    suspend fun deleteByUser(userId: UserId)
}
