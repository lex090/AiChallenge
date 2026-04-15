package com.ai.challenge.contextmanagement.memory

import com.ai.challenge.contextmanagement.model.UserFact

/**
 * Domain Service -- user fact memory provider with replace-all write semantics.
 *
 * Manages LLM-extracted user facts that span across all sessions and projects for a given user.
 * Facts are identified by the composite key (userId, category, key); the full set is replaced atomically.
 *
 * Invariants:
 * - Only supports [MemoryScope.User]; Session and Project scopes are rejected at runtime.
 * - [get] returns an empty list if the user has no extracted facts.
 * - [replace] deletes all existing facts for the user and inserts the provided [facts] atomically.
 */
interface UserFactMemoryProvider : MemoryProvider<List<UserFact>> {
    /** Replaces all facts for the user identified by [scope] with the given [facts] list. */
    suspend fun replace(scope: MemoryScope, facts: List<UserFact>)
}
