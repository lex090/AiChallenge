package com.ai.challenge.contextmanagement.memory

import com.ai.challenge.contextmanagement.model.UserPreferencesMemory

/**
 * Domain Service -- user preferences memory provider with upsert write semantics.
 *
 * Manages user-level persistent preferences, instructions, and communication style
 * preferences that span across all sessions and projects for a given user.
 *
 * Invariants:
 * - Only supports [MemoryScope.User]; Session and Project scopes are rejected at runtime.
 * - [save] inserts or replaces preferences for the given user scope.
 * - [get] returns null if no preferences have been saved yet.
 */
interface UserPreferencesMemoryProvider : MemoryProvider<UserPreferencesMemory?> {
    /** Upserts [preferences] for the user identified by [scope]. */
    suspend fun save(scope: MemoryScope, preferences: UserPreferencesMemory)
}
