package com.ai.challenge.contextmanagement.data

import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for project instructions in memory.db.
 *
 * One row per project. Upsert semantics managed by repository.
 */
object ProjectInstructionsTable : Table("project_instructions") {
    val projectId = varchar("project_id", 36)
    val content = text("content")
    override val primaryKey = PrimaryKey(projectId)
}
