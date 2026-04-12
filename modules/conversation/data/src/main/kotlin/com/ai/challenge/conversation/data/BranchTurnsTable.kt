package com.ai.challenge.conversation.data

import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for the "branch_turns" join table.
 *
 * Maps [com.ai.challenge.conversation.model.Turn] entities to
 * [com.ai.challenge.conversation.model.Branch] entities with ordering.
 * [orderIndex] maintains chronological turn order within a branch.
 */
object BranchTurnsTable : Table("branch_turns") {
    val branchId = varchar("branch_id", 36)
    val turnId = varchar("turn_id", 36)
    val orderIndex = integer("order_index")

    override val primaryKey = PrimaryKey(branchId, turnId)
}
