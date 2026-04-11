package com.ai.challenge.core.token

data class TokenDetails(
    val promptTokens: Int,
    val completionTokens: Int,
    val cachedTokens: Int,
    val cacheWriteTokens: Int,
    val reasoningTokens: Int,
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
