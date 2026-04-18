package com.ai.challenge.conversation.data

import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for the "projects" table.
 *
 * Stores [com.ai.challenge.conversation.model.Project] aggregate root data.
 */
object ProjectsTable : Table("projects") {
    val id = varchar("id", 36)
    val name = varchar("name", 255)
    val systemInstructions = text("system_instructions")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}
