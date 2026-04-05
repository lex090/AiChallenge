package com.ai.challenge.fact.repository

import org.jetbrains.exposed.sql.Table

object FactsTable : Table("facts") {
    val id = integer("id").autoIncrement()
    val sessionId = varchar("session_id", 36)
    val key = varchar("key", 256)
    val value = text("value")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}
