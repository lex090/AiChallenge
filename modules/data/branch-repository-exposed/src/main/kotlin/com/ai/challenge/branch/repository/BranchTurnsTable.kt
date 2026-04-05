package com.ai.challenge.branch.repository

import org.jetbrains.exposed.sql.Table

object BranchTurnsTable : Table("branch_turns") {
    val id = varchar("id", 36)
    val branchId = varchar("branch_id", 36)
    val userMessage = text("user_message")
    val agentResponse = text("agent_response")
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)
}
