package com.ai.challenge.contextmanagement.memory

import com.ai.challenge.contextmanagement.model.UserNote

/**
 * Domain Service -- user note memory provider with append/update/delete write semantics.
 *
 * Manages user-authored notes that span across all sessions and projects for a given user.
 * A user may have many notes; each note is independently addressable by its identity.
 *
 * Invariants:
 * - Only supports [MemoryScope.User]; Session and Project scopes are rejected at runtime.
 * - [get] returns an empty list if the user has no notes.
 * - [append] and [update] both delegate to the repository upsert keyed on [UserNote.id].
 * - [delete] is idempotent -- deleting a non-existent note is a no-op.
 */
interface UserNoteMemoryProvider : MemoryProvider<List<UserNote>> {
    /** Appends (upserts) [note] into the user's note collection. */
    suspend fun append(scope: MemoryScope, note: UserNote)
    /** Updates (upserts) [note], replacing any existing note with the same identity. */
    suspend fun update(scope: MemoryScope, note: UserNote)
    /** Deletes [note] from the user's note collection. No-op if not present. */
    suspend fun delete(scope: MemoryScope, note: UserNote)
}
