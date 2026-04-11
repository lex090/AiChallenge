package com.ai.challenge.fact.repository

import org.jetbrains.exposed.sql.Table

object FactsTable : Table("facts") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val category = varchar("category", 50)
    val key = varchar("key", 255)
    val value = text("value")

    override val primaryKey = PrimaryKey(id)
}
