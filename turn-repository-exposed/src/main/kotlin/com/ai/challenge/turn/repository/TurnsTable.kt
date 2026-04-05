package com.ai.challenge.turn.repository

import org.jetbrains.exposed.sql.Table

object TurnsTable : Table("turns") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val userMessage = text("user_message")
    val agentResponse = text("agent_response")
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)
}
