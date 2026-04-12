package com.ai.challenge.conversation.data

import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for the "branches" table.
 *
 * Stores [com.ai.challenge.conversation.model.Branch] entity data.
 * [sourceTurnId] is nullable -- null indicates the main branch.
 */
object BranchesTable : Table("branches") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val sourceTurnId = varchar("source_turn_id", 36).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
