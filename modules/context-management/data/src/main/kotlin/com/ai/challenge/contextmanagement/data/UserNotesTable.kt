package com.ai.challenge.contextmanagement.data

import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for persisting [com.ai.challenge.contextmanagement.model.UserNote] entities.
 *
 * Maps to the "user_notes" SQLite table in memory.db.
 * Each row represents a single user-authored note tied to a user via [userId].
 */
object UserNotesTable : Table("user_notes") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val title = text("title")
    val content = text("content")
    override val primaryKey = PrimaryKey(id)
}
