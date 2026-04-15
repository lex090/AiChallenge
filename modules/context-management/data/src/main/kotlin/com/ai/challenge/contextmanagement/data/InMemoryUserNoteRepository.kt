package com.ai.challenge.contextmanagement.data

import com.ai.challenge.contextmanagement.model.UserNote
import com.ai.challenge.contextmanagement.repository.UserNoteRepository
import com.ai.challenge.sharedkernel.identity.UserNoteId
import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Stub in-memory implementation of [UserNoteRepository].
 *
 * Used as a temporary placeholder until the Exposed (SQLite) implementation
 * is added in Task 12. Data is not persisted across application restarts.
 */
class InMemoryUserNoteRepository : UserNoteRepository {

    private val store: MutableMap<String, UserNote> = mutableMapOf()

    override suspend fun save(note: UserNote) {
        store[note.id.value] = note
    }

    override suspend fun getByUser(userId: UserId): List<UserNote> =
        store.values.filter { note -> note.userId == userId }

    override suspend fun delete(noteId: UserNoteId) {
        store.remove(noteId.value)
    }

    override suspend fun deleteByUser(userId: UserId) {
        store.values.removeIf { note -> note.userId == userId }
    }
}
