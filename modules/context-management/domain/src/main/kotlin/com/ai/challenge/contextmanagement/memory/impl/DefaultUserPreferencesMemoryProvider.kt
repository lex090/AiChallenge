package com.ai.challenge.contextmanagement.memory.impl

import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.UserPreferencesMemoryProvider
import com.ai.challenge.contextmanagement.model.UserPreferencesMemory
import com.ai.challenge.contextmanagement.repository.UserPreferencesMemoryRepository
import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Default implementation of [UserPreferencesMemoryProvider].
 *
 * Delegates to [UserPreferencesMemoryRepository] for persistence.
 * Translates [MemoryScope] to [UserId]; rejects non-User scopes with a runtime error.
 *
 * Invariants:
 * - Only [MemoryScope.User] is valid; other scopes throw [IllegalStateException].
 * - [clear] deletes preferences when scope is User; is a no-op for Session and Project.
 */
class DefaultUserPreferencesMemoryProvider(
    private val userPreferencesMemoryRepository: UserPreferencesMemoryRepository,
) : UserPreferencesMemoryProvider {

    override suspend fun get(scope: MemoryScope): UserPreferencesMemory? {
        val userId = scope.toUserId()
        return userPreferencesMemoryRepository.getByUser(userId = userId)
    }

    override suspend fun save(scope: MemoryScope, preferences: UserPreferencesMemory) {
        userPreferencesMemoryRepository.save(preferences = preferences)
    }

    override suspend fun clear(scope: MemoryScope) {
        when (scope) {
            is MemoryScope.User -> userPreferencesMemoryRepository.deleteByUser(userId = scope.userId)
            is MemoryScope.Session -> Unit
            is MemoryScope.Project -> Unit
        }
    }

    private fun MemoryScope.toUserId(): UserId = when (this) {
        is MemoryScope.User -> userId
        is MemoryScope.Session -> error("UserPreferencesMemoryProvider does not support Session scope")
        is MemoryScope.Project -> error("UserPreferencesMemoryProvider does not support Project scope")
    }
}
