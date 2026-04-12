package com.ai.challenge.contextmanagement.data

import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for persisting [com.ai.challenge.contextmanagement.model.Fact] value objects.
 *
 * Maps to the "facts" SQLite table. Each row stores a single fact
 * correlated with a session via [sessionId].
 */
object FactsTable : Table("facts") {
    val id = integer("id").autoIncrement()
    val sessionId = varchar("session_id", 36)
    val category = varchar("category", 50)
    val key = varchar("key", 255)
    val value = text("value")

    override val primaryKey = PrimaryKey(id)
}
