package com.ai.challenge.session.repository

import org.jetbrains.exposed.sql.Table

object SessionsTable : Table("sessions") {
    val id = varchar("id", 36)
    val title = varchar("title", 255)
    val contextManagementType = varchar("context_management_type", 50)
    val activeBranchId = varchar("active_branch_id", 36)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}
