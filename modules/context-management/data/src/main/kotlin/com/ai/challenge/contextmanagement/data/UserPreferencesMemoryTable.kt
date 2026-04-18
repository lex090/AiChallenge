package com.ai.challenge.contextmanagement.data

import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for persisting [com.ai.challenge.contextmanagement.model.UserPreferencesMemory].
 *
 * Maps to the "user_preferences_memory" SQLite table in memory.db.
 * One row per user; upsert semantics managed by the repository.
 */
object UserPreferencesMemoryTable : Table("user_preferences_memory") {
    val userId = varchar("user_id", 36)
    val content = text("content")
    override val primaryKey = PrimaryKey(userId)
}
