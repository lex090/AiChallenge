package com.ai.challenge.contextmanagement.data

import com.ai.challenge.contextmanagement.model.NoteContent
import com.ai.challenge.contextmanagement.model.NoteTitle
import com.ai.challenge.contextmanagement.model.UserNote
import com.ai.challenge.contextmanagement.repository.UserNoteRepository
import com.ai.challenge.sharedkernel.identity.UserNoteId
import com.ai.challenge.sharedkernel.identity.UserId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert

/**
 * Exposed-backed implementation of [UserNoteRepository].
 *
 * Persists [UserNote] entities in the "user_notes" SQLite table inside memory.db.
 * Uses upsert semantics keyed on [UserNote.id] so that saving an existing note
 * replaces it in place.
 */
class ExposedUserNoteRepository(
    private val database: Database,
) : UserNoteRepository {

    override suspend fun save(note: UserNote) {
        newSuspendedTransaction(db = database) {
            UserNotesTable.upsert {
                it[id] = note.id.value
                it[userId] = note.userId.value
                it[title] = note.title.value
                it[content] = note.content.value
            }
        }
    }

    override suspend fun getByUser(userId: UserId): List<UserNote> {
        return newSuspendedTransaction(db = database) {
            UserNotesTable
                .selectAll()
                .where { UserNotesTable.userId eq userId.value }
                .map { row ->
                    UserNote(
                        id = UserNoteId(value = row[UserNotesTable.id]),
                        userId = UserId(value = row[UserNotesTable.userId]),
                        title = NoteTitle(value = row[UserNotesTable.title]),
                        content = NoteContent(value = row[UserNotesTable.content]),
                    )
                }
        }
    }

    override suspend fun delete(noteId: UserNoteId) {
        newSuspendedTransaction(db = database) {
            UserNotesTable.deleteWhere { UserNotesTable.id eq noteId.value }
        }
    }

    override suspend fun deleteByUser(userId: UserId) {
        newSuspendedTransaction(db = database) {
            UserNotesTable.deleteWhere { UserNotesTable.userId eq userId.value }
        }
    }
}
