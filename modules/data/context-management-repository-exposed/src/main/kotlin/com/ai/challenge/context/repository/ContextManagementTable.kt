package com.ai.challenge.context.repository

import org.jetbrains.exposed.sql.Table

object ContextManagementTable : Table("context_management") {
    val sessionId = varchar("session_id", 36)
    val type = varchar("type", 50)

    override val primaryKey = PrimaryKey(sessionId)
}
