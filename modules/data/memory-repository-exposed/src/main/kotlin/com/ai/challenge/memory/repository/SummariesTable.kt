package com.ai.challenge.memory.repository

import org.jetbrains.exposed.sql.Table

object SummariesTable : Table("summaries") {
    val id = integer("id").autoIncrement()
    val sessionId = varchar("session_id", 36)
    val text = text("text")
    val fromTurnIndex = integer("from_turn_index")
    val toTurnIndex = integer("to_turn_index")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
