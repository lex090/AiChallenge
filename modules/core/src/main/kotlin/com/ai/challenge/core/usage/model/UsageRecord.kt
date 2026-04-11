package com.ai.challenge.core.usage.model

/**
 * Composite Value Object — usage metrics for a single turn.
 *
 * Merges tokens and cost into a single unit.
 * Replaces two separate objects (TokenDetails + CostDetails)
 * that were always created, stored, and read together.
 *
 * Preserves full granularity from OpenRouter API:
 * 5 token fields (prompt, completion, cached, cacheWrite, reasoning)
 * 4 cost fields (total, upstream, upstreamPrompt, upstreamCompletion)
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
}
