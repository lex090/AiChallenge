package com.ai.challenge.core.metrics

data class TokenDetails(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val cachedTokens: Int = 0,
    val cacheWriteTokens: Int = 0,
    val reasoningTokens: Int = 0,
) {
    val totalTokens: Int get() = promptTokens + completionTokens

    operator fun plus(other: TokenDetails) = TokenDetails(
        promptTokens = promptTokens + other.promptTokens,
        completionTokens = completionTokens + other.completionTokens,
        cachedTokens = cachedTokens + other.cachedTokens,
        cacheWriteTokens = cacheWriteTokens + other.cacheWriteTokens,
        reasoningTokens = reasoningTokens + other.reasoningTokens,
    )
}
