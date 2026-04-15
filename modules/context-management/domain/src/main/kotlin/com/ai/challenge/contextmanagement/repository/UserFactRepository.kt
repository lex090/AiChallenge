package com.ai.challenge.contextmanagement.repository

import com.ai.challenge.contextmanagement.model.UserFact
import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Repository port -- persistence contract for [UserFact].
 *
 * Defines the storage boundary for LLM-extracted user facts within the
 * Context Management BC. Facts are identified by the composite key
 * ([UserFact.userId], [UserFact.category], [UserFact.key]).
 *
 * Invariants:
 * - [saveAll] replaces the entire fact set for [userId] atomically (delete-then-insert semantics).
 * - [getByUser] returns an empty list when the user has no facts.
 * - [deleteByUser] is idempotent -- calling it for an unknown user is a no-op.
 */
interface UserFactRepository {
    /** Replaces all facts for [userId] with the provided [facts] list. */
    suspend fun saveAll(userId: UserId, facts: List<UserFact>)
    /** Returns all facts extracted for [userId], or an empty list if none exist. */
    suspend fun getByUser(userId: UserId): List<UserFact>
    /** Deletes all facts for [userId]. No-op if none exist. */
    suspend fun deleteByUser(userId: UserId)
}
