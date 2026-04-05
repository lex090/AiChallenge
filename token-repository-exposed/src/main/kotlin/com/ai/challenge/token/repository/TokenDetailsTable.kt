package com.ai.challenge.token.repository

import org.jetbrains.exposed.sql.Table

object TokenDetailsTable : Table("token_details") {
    val turnId = varchar("turn_id", 36)
    val sessionId = varchar("session_id", 36)
    val promptTokens = integer("prompt_tokens")
    val completionTokens = integer("completion_tokens")
    val cachedTokens = integer("cached_tokens")
    val cacheWriteTokens = integer("cache_write_tokens")
    val reasoningTokens = integer("reasoning_tokens")

    override val primaryKey = PrimaryKey(turnId)
}
