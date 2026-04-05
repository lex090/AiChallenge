package com.ai.challenge.core.metrics

data class CostDetails(
    val totalCost: Double = 0.0,
    val upstreamCost: Double = 0.0,
    val upstreamPromptCost: Double = 0.0,
    val upstreamCompletionsCost: Double = 0.0,
) {
    operator fun plus(other: CostDetails) = CostDetails(
        totalCost = totalCost + other.totalCost,
        upstreamCost = upstreamCost + other.upstreamCost,
        upstreamPromptCost = upstreamPromptCost + other.upstreamPromptCost,
        upstreamCompletionsCost = upstreamCompletionsCost + other.upstreamCompletionsCost,
    )
}
