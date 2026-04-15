package com.ai.challenge.contextmanagement.data

import com.ai.challenge.contextmanagement.model.UserFact
import com.ai.challenge.contextmanagement.repository.UserFactRepository
import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Stub in-memory implementation of [UserFactRepository].
 *
 * Used as a temporary placeholder until the Exposed (SQLite) implementation
 * is added in Task 12. Data is not persisted across application restarts.
 */
class InMemoryUserFactRepository : UserFactRepository {

    private val store: MutableMap<String, List<UserFact>> = mutableMapOf()

    override suspend fun saveAll(userId: UserId, facts: List<UserFact>) {
        store[userId.value] = facts
    }

    override suspend fun getByUser(userId: UserId): List<UserFact> =
        store[userId.value] ?: emptyList()

    override suspend fun deleteByUser(userId: UserId) {
        store.remove(userId.value)
    }
}
