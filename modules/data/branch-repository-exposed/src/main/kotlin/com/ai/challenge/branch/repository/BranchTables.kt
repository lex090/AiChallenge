package com.ai.challenge.branch.repository

import org.jetbrains.exposed.sql.Table

object BranchesTable : Table("branches") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val name = varchar("name", 255)
    val checkpointTurnIndex = integer("checkpoint_turn_index")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object BranchTurnsTable : Table("branch_turns") {
    val id = varchar("id", 36)
    val branchId = varchar("branch_id", 36)
    val userMessage = text("user_message")
    val agentResponse = text("agent_response")
    val timestamp = long("timestamp")
    val sortOrder = integer("sort_order")

    override val primaryKey = PrimaryKey(id)
}

object ActiveBranchesTable : Table("active_branches") {
    val sessionId = varchar("session_id", 36)
    val branchId = varchar("branch_id", 36)

    override val primaryKey = PrimaryKey(sessionId)
}
