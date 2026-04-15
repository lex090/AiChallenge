package com.ai.challenge.contextmanagement.repository

import com.ai.challenge.contextmanagement.model.UserNote
import com.ai.challenge.sharedkernel.identity.UserNoteId
import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Repository port -- persistence contract for [UserNote].
 *
 * Defines the storage boundary for user-authored notes within the
 * Context Management BC. A user may have many notes; each note belongs to
 * exactly one user.
 *
 * Invariants:
 * - [save] performs an upsert keyed on [UserNote.id].
 * - [getByUser] returns an empty list when the user has no notes.
 * - [delete] and [deleteByUser] are idempotent.
 */
interface UserNoteRepository {
    /** Upserts the given [note]. */
    suspend fun save(note: UserNote)
    /** Returns all notes owned by [userId], or an empty list if none exist. */
    suspend fun getByUser(userId: UserId): List<UserNote>
    /** Deletes the note identified by [noteId]. No-op if it does not exist. */
    suspend fun delete(noteId: UserNoteId)
    /** Deletes all notes owned by [userId]. No-op if none exist. */
    suspend fun deleteByUser(userId: UserId)
}
