package com.ai.challenge.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
    val model: String? = null,
    val usage: Usage? = null,
    val error: ErrorBody? = null,
    val cost: Double? = null,
    @SerialName("cost_details") val costDetails: ResponseCostDetails? = null,
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: Message,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    @SerialName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null,
    @SerialName("completion_tokens_details") val completionTokensDetails: CompletionTokensDetails? = null,
    val cost: Double? = null,
    @SerialName("cost_details") val costDetails: ResponseCostDetails? = null,
)

@Serializable
data class PromptTokensDetails(
    @SerialName("cached_tokens") val cachedTokens: Int = 0,
    @SerialName("cache_write_tokens") val cacheWriteTokens: Int = 0,
)

@Serializable
data class CompletionTokensDetails(
    @SerialName("reasoning_tokens") val reasoningTokens: Int = 0,
)

@Serializable
data class ResponseCostDetails(
    @SerialName("upstream_inference_cost") val upstreamCost: Double = 0.0,
    @SerialName("upstream_inference_prompt_cost") val upstreamPromptCost: Double = 0.0,
    @SerialName("upstream_inference_completions_cost") val upstreamCompletionsCost: Double = 0.0,
)

@Serializable
data class ErrorBody(val message: String? = null, val code: Int? = null)
