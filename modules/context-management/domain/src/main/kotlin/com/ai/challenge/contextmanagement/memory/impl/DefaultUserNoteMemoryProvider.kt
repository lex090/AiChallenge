package com.ai.challenge.contextmanagement.memory.impl

import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.UserNoteMemoryProvider
import com.ai.challenge.contextmanagement.model.UserNote
import com.ai.challenge.contextmanagement.repository.UserNoteRepository
import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Default implementation of [UserNoteMemoryProvider].
 *
 * Delegates to [UserNoteRepository] for persistence.
 * Translates [MemoryScope] to [UserId]; rejects non-User scopes with a runtime error.
 *
 * Invariants:
 * - Only [MemoryScope.User] is valid for read operations; other scopes throw [IllegalStateException].
 * - [append] and [update] both call [UserNoteRepository.save] (upsert semantics keyed on note id).
 * - [delete] calls [UserNoteRepository.delete] by note id; idempotent.
 * - [clear] deletes all notes when scope is User; is a no-op for Session and Project.
 */
class DefaultUserNoteMemoryProvider(
    private val userNoteRepository: UserNoteRepository,
) : UserNoteMemoryProvider {

    override suspend fun get(scope: MemoryScope): List<UserNote> {
        val userId = scope.toUserId()
        return userNoteRepository.getByUser(userId = userId)
    }

    override suspend fun append(scope: MemoryScope, note: UserNote) {
        userNoteRepository.save(note = note)
    }

    override suspend fun update(scope: MemoryScope, note: UserNote) {
        userNoteRepository.save(note = note)
    }

    override suspend fun delete(scope: MemoryScope, note: UserNote) {
        userNoteRepository.delete(noteId = note.id)
    }

    override suspend fun clear(scope: MemoryScope) {
        when (scope) {
            is MemoryScope.User -> userNoteRepository.deleteByUser(userId = scope.userId)
            is MemoryScope.Session -> Unit
            is MemoryScope.Project -> Unit
        }
    }

    private fun MemoryScope.toUserId(): UserId = when (this) {
        is MemoryScope.User -> userId
        is MemoryScope.Session -> error("UserNoteMemoryProvider does not support Session scope")
        is MemoryScope.Project -> error("UserNoteMemoryProvider does not support Project scope")
    }
}
