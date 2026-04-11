package com.ai.challenge.session.repository

import org.jetbrains.exposed.sql.Table

object BranchTurnsTable : Table("branch_turns") {
    val branchId = varchar("branch_id", 36)
    val turnId = varchar("turn_id", 36)
    val orderIndex = integer("order_index")

    override val primaryKey = PrimaryKey(branchId, turnId)
}
