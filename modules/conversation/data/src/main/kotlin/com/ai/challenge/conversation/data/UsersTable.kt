package com.ai.challenge.conversation.data

import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for the "users" table.
 *
 * Stores [com.ai.challenge.conversation.model.User] aggregate root data.
 * [preferences] stores the [com.ai.challenge.conversation.model.UserPreferences] value as plain text.
 */
object UsersTable : Table("users") {
    val id = varchar("id", 36)
    val name = varchar("name", 255)
    val preferences = text("preferences")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}
