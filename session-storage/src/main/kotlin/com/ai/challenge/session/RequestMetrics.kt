package com.ai.challenge.session

data class RequestMetrics(
    val tokens: TokenDetails = TokenDetails(),
    val cost: CostDetails = CostDetails(),
) {
    operator fun plus(other: RequestMetrics) = RequestMetrics(
        tokens = tokens + other.tokens,
        cost = cost + other.cost,
    )
}
