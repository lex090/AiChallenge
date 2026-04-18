package com.ai.challenge.contextmanagement.data

import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for persisting [com.ai.challenge.contextmanagement.model.UserFact] entities.
 *
 * Maps to the "user_facts" SQLite table in memory.db.
 * The composite primary key ([userId], [category], [key]) enforces one value
 * per fact key per user per category.
 */
object UserFactsTable : Table("user_facts") {
    val userId = varchar("user_id", 36)
    val category = varchar("category", 50)
    val key = varchar("key", 255)
    val value = text("value")
    override val primaryKey = PrimaryKey(userId, category, key)
}
