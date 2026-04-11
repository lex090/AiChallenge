package com.ai.challenge.session.repository

import org.jetbrains.exposed.sql.Table

object BranchesTable : Table("branches") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val parentId = varchar("parent_id", 36).nullable()
    val name = varchar("name", 255)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
