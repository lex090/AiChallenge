package com.ai.challenge.branch.repository

import org.jetbrains.exposed.sql.Table

object BranchesTable : Table("branches") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val name = varchar("name", 255)
    val parentBranchId = varchar("parent_branch_id", 36).nullable()
    val isActive = bool("is_active")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
