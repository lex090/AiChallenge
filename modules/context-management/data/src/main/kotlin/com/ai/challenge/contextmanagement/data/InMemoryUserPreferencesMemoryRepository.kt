package com.ai.challenge.contextmanagement.data

import com.ai.challenge.contextmanagement.model.UserPreferencesMemory
import com.ai.challenge.contextmanagement.repository.UserPreferencesMemoryRepository
import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Stub in-memory implementation of [UserPreferencesMemoryRepository].
 *
 * Used as a temporary placeholder until the Exposed (SQLite) implementation
 * is added in Task 12. Data is not persisted across application restarts.
 */
class InMemoryUserPreferencesMemoryRepository : UserPreferencesMemoryRepository {

    private val store: MutableMap<String, UserPreferencesMemory> = mutableMapOf()

    override suspend fun save(preferences: UserPreferencesMemory) {
        store[preferences.userId.value] = preferences
    }

    override suspend fun getByUser(userId: UserId): UserPreferencesMemory? =
        store[userId.value]

    override suspend fun deleteByUser(userId: UserId) {
        store.remove(userId.value)
    }
}
