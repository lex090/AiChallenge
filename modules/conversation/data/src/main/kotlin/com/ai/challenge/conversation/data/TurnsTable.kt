package com.ai.challenge.conversation.data

import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for the "turns" table.
 *
 * Stores [com.ai.challenge.conversation.model.Turn] entity data
 * including embedded [com.ai.challenge.conversation.model.UsageRecord] fields.
 * Cost values stored as varchar to preserve [java.math.BigDecimal] precision.
 */
object TurnsTable : Table("turns") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val userMessage = text("user_message")
    val assistantMessage = text("assistant_message")
    val createdAt = long("created_at")
    val promptTokens = integer("prompt_tokens")
    val completionTokens = integer("completion_tokens")
    val cachedTokens = integer("cached_tokens")
    val cacheWriteTokens = integer("cache_write_tokens")
    val reasoningTokens = integer("reasoning_tokens")
    val totalCost = varchar("total_cost", 50)
    val upstreamCost = varchar("upstream_cost", 50)
    val upstreamPromptCost = varchar("upstream_prompt_cost", 50)
    val upstreamCompletionsCost = varchar("upstream_completions_cost", 50)

    override val primaryKey = PrimaryKey(id)
}
