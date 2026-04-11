package com.ai.challenge.session.repository

import org.jetbrains.exposed.sql.Table

object BranchesTable : Table("branches") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val sourceTurnId = varchar("source_turn_id", 36).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
