package com.ai.challenge.contextmanagement.memory.impl

import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.UserFactMemoryProvider
import com.ai.challenge.contextmanagement.model.UserFact
import com.ai.challenge.contextmanagement.repository.UserFactRepository
import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Default implementation of [UserFactMemoryProvider].
 *
 * Delegates to [UserFactRepository] for persistence.
 * Translates [MemoryScope] to [UserId]; rejects non-User scopes with a runtime error.
 *
 * Invariants:
 * - Only [MemoryScope.User] is valid for read operations; other scopes throw [IllegalStateException].
 * - [replace] atomically replaces all facts for the user (delete-then-insert via repository).
 * - [clear] deletes all facts when scope is User; is a no-op for Session and Project.
 */
class DefaultUserFactMemoryProvider(
    private val userFactRepository: UserFactRepository,
) : UserFactMemoryProvider {

    override suspend fun get(scope: MemoryScope): List<UserFact> {
        val userId = scope.toUserId()
        return userFactRepository.getByUser(userId = userId)
    }

    override suspend fun replace(scope: MemoryScope, facts: List<UserFact>) {
        val userId = scope.toUserId()
        userFactRepository.saveAll(userId = userId, facts = facts)
    }

    override suspend fun clear(scope: MemoryScope) {
        when (scope) {
            is MemoryScope.User -> userFactRepository.deleteByUser(userId = scope.userId)
            is MemoryScope.Session -> Unit
            is MemoryScope.Project -> Unit
        }
    }

    private fun MemoryScope.toUserId(): UserId = when (this) {
        is MemoryScope.User -> userId
        is MemoryScope.Session -> error("UserFactMemoryProvider does not support Session scope")
        is MemoryScope.Project -> error("UserFactMemoryProvider does not support Project scope")
    }
}
