package com.ai.challenge.fact.repository

import org.jetbrains.exposed.sql.Table

object FactsTable : Table("facts") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val content = text("content")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
