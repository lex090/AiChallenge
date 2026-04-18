package com.ai.challenge.sharedkernel.identity

import java.util.UUID

/**
 * Typed identifier for entity UserNote.
 *
 * Value class over String (UUID). Ensures type safety --
 * impossible to accidentally pass [UserId] or [AgentSessionId]
 * where [UserNoteId] is expected.
 *
 * Generation: [generate] creates a new unique identifier.
 */
@JvmInline
value class UserNoteId(val value: String) {
    companion object {
        fun generate(): UserNoteId = UserNoteId(value = UUID.randomUUID().toString())
    }
}
