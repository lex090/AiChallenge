package com.ai.challenge.contextmanagement.model

/**
 * Value Object -- title of a user note.
 *
 * Identifies a [UserNote] with a human-readable label.
 * Has no identity -- equality is defined solely by its string [value].
 *
 * Invariants:
 * - [value] must not be blank when used.
 */
@JvmInline
value class NoteTitle(val value: String)
