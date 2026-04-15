package com.ai.challenge.contextmanagement.model

import com.ai.challenge.sharedkernel.identity.UserId
import com.ai.challenge.sharedkernel.identity.UserNoteId

/**
 * Entity -- a user-authored note stored in user-scoped memory.
 *
 * Belongs to the user memory sub-domain of the Context Management BC.
 * Models a discrete piece of knowledge that a user explicitly saves for
 * the LLM to reference across sessions (e.g. a project summary, a reference
 * snippet, a recurring instruction).
 *
 * Invariants:
 * - [id] uniquely identifies the note across the system.
 * - [userId] ties the note to exactly one user; notes are never shared.
 * - [title] must not be blank.
 * - [content] must not be blank.
 */
data class UserNote(
    val id: UserNoteId,
    val userId: UserId,
    val title: NoteTitle,
    val content: NoteContent,
)
