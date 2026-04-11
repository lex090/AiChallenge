package com.ai.challenge.core.cost

data class CostDetails(
    val totalCost: Double,
    val upstreamCost: Double,
    val upstreamPromptCost: Double,
    val upstreamCompletionsCost: Double,
) {
    operator fun plus(other: CostDetails) = CostDetails(
        totalCost = totalCost + other.totalCost,
        upstreamCost = upstreamCost + other.upstreamCost,
        upstreamPromptCost = upstreamPromptCost + other.upstreamPromptCost,
        upstreamCompletionsCost = upstreamCompletionsCost + other.upstreamCompletionsCost,
    )
}
