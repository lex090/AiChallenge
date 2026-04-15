package com.ai.challenge.contextmanagement.model

/**
 * Value Object -- body text of a user note.
 *
 * Carries the free-form markdown or plain-text body of a [UserNote].
 * Has no identity -- equality is defined solely by its string [value].
 *
 * Invariants:
 * - [value] must not be blank when persisted.
 */
@JvmInline
value class NoteContent(val value: String)
