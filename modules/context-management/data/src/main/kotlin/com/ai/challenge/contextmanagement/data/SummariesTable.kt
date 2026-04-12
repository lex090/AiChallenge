package com.ai.challenge.contextmanagement.data

import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for persisting [com.ai.challenge.contextmanagement.model.Summary] value objects.
 *
 * Maps to the "summaries" SQLite table. Each row stores a single summary
 * correlated with a session via [sessionId].
 */
object SummariesTable : Table("summaries") {
    val id = integer("id").autoIncrement()
    val sessionId = varchar("session_id", 36)
    val text = text("text")
    val fromTurnIndex = integer("from_turn_index")
    val toTurnIndex = integer("to_turn_index")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
