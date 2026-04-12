package com.ai.challenge.sharedkernel.vo

/**
 * Value Object -- lightweight usage metrics from an LLM completion.
 *
 * Shared kernel counterpart to Conversation's UsageRecord.
 * Contains only the essential token counts needed at domain boundaries.
 * Conversation bounded context maps this to its richer UsageRecord
 * (with cost fields) internally.
 *
 * Invariants:
 * - All token counts are non-negative.
 */
data class LlmUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
)
