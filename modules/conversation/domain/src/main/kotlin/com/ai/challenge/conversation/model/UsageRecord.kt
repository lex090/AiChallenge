package com.ai.challenge.conversation.model

import java.math.BigDecimal

/**
 * Value Object -- usage metrics for a single [Turn].
 *
 * Has no identity -- defined only by its attributes.
 * Immutable. Created once with [Turn] and never modified.
 * Two UsageRecords with identical fields are fully interchangeable.
 *
 * Supports aggregation through [plus] operator for computing
 * session-level totals via UsageQueryService.
 *
 * Preserves full granularity from OpenRouter API:
 * 5 token fields (prompt, completion, cached, cacheWrite, reasoning)
 * 4 cost fields (total, upstream, upstreamPrompt, upstreamCompletion)
 *
 * Embedded in [Turn] as a composite part -- not stored separately.
 */
data class UsageRecord(
    val promptTokens: TokenCount,
    val completionTokens: TokenCount,
    val cachedTokens: TokenCount,
    val cacheWriteTokens: TokenCount,
    val reasoningTokens: TokenCount,
    val totalCost: Cost,
    val upstreamCost: Cost,
    val upstreamPromptCost: Cost,
    val upstreamCompletionsCost: Cost,
) {
    val totalTokens: TokenCount
        get() = promptTokens + completionTokens

    operator fun plus(other: UsageRecord): UsageRecord =
        UsageRecord(
            promptTokens = promptTokens + other.promptTokens,
            completionTokens = completionTokens + other.completionTokens,
            cachedTokens = cachedTokens + other.cachedTokens,
            cacheWriteTokens = cacheWriteTokens + other.cacheWriteTokens,
            reasoningTokens = reasoningTokens + other.reasoningTokens,
            totalCost = totalCost + other.totalCost,
            upstreamCost = upstreamCost + other.upstreamCost,
            upstreamPromptCost = upstreamPromptCost + other.upstreamPromptCost,
            upstreamCompletionsCost = upstreamCompletionsCost + other.upstreamCompletionsCost,
        )

    companion object {
        val ZERO: UsageRecord = UsageRecord(
            promptTokens = TokenCount(value = 0),
            completionTokens = TokenCount(value = 0),
            cachedTokens = TokenCount(value = 0),
            cacheWriteTokens = TokenCount(value = 0),
            reasoningTokens = TokenCount(value = 0),
            totalCost = Cost(value = BigDecimal.ZERO),
            upstreamCost = Cost(value = BigDecimal.ZERO),
            upstreamPromptCost = Cost(value = BigDecimal.ZERO),
            upstreamCompletionsCost = Cost(value = BigDecimal.ZERO),
        )
    }
}
