package com.ai.challenge.conversation.data

import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for the "sessions" table.
 *
 * Stores [com.ai.challenge.conversation.model.AgentSession] aggregate root data.
 * [contextManagementType] stores the [com.ai.challenge.sharedkernel.vo.ContextModeId] value as a plain string.
 */
object SessionsTable : Table("sessions") {
    val id = varchar("id", 36)
    val title = varchar("title", 255)
    val contextManagementType = varchar("context_management_type", 50)
    val projectId = varchar("project_id", 36).nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}
