package com.ai.challenge.cost.repository

import org.jetbrains.exposed.sql.Table

object CostDetailsTable : Table("cost_details") {
    val turnId = varchar("turn_id", 36)
    val sessionId = varchar("session_id", 36)
    val totalCost = double("total_cost")
    val upstreamCost = double("upstream_cost")
    val upstreamPromptCost = double("upstream_prompt_cost")
    val upstreamCompletionsCost = double("upstream_completions_cost")

    override val primaryKey = PrimaryKey(turnId)
}
