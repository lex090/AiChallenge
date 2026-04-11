package com.ai.challenge.llm

import arrow.core.Either
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.llm.LlmPort
import com.ai.challenge.core.llm.LlmResponse
import com.ai.challenge.core.llm.ResponseFormat
import com.ai.challenge.core.usage.model.Cost
import com.ai.challenge.core.usage.model.TokenCount
import com.ai.challenge.core.usage.model.UsageRecord
import com.ai.challenge.llm.model.ChatResponse
import java.math.BigDecimal

/**
 * Adapter — translates between domain [LlmPort] and OpenRouter API.
 *
 * Anti-Corruption Layer implementation: maps domain [ContextMessage]
 * and [ResponseFormat] to OpenRouter DSL calls, and maps
 * [ChatResponse] back to domain [LlmResponse].
 */
class OpenRouterAdapter(
    private val openRouterService: OpenRouterService,
    private val model: String,
) : LlmPort {

    override suspend fun complete(
        messages: List<ContextMessage>,
        responseFormat: ResponseFormat,
    ): Either<DomainError, LlmResponse> =
        try {
            val response = openRouterService.chat(model = model) {
                if (responseFormat is ResponseFormat.Json) {
                    jsonMode = true
                }
                for (msg in messages) {
                    when (msg.role) {
                        MessageRole.System -> system(content = msg.content.value)
                        MessageRole.User -> user(content = msg.content.value)
                        MessageRole.Assistant -> assistant(content = msg.content.value)
                    }
                }
            }

            if (response.error != null) {
                Either.Left(value = DomainError.ApiError(message = response.error!!.message ?: "Unknown API error"))
            } else {
                val text = response.choices.firstOrNull()?.message?.content
                    ?: return Either.Left(value = DomainError.ApiError(message = "Empty response from LLM"))
                Either.Right(
                    value = LlmResponse(
                        content = MessageContent(value = text),
                        usage = mapUsage(response = response),
                    )
                )
            }
        } catch (e: Exception) {
            Either.Left(value = DomainError.NetworkError(message = e.message ?: "Unknown network error"))
        }

    private fun mapUsage(response: ChatResponse): UsageRecord = UsageRecord(
        promptTokens = TokenCount(value = response.usage?.promptTokens ?: 0),
        completionTokens = TokenCount(value = response.usage?.completionTokens ?: 0),
        cachedTokens = TokenCount(value = response.usage?.promptTokensDetails?.cachedTokens ?: 0),
        cacheWriteTokens = TokenCount(value = response.usage?.promptTokensDetails?.cacheWriteTokens ?: 0),
        reasoningTokens = TokenCount(value = response.usage?.completionTokensDetails?.reasoningTokens ?: 0),
        totalCost = Cost(value = BigDecimal.valueOf(response.usage?.cost ?: response.cost ?: 0.0)),
        upstreamCost = Cost(value = BigDecimal.valueOf(response.usage?.costDetails?.upstreamCost ?: response.costDetails?.upstreamCost ?: 0.0)),
        upstreamPromptCost = Cost(value = BigDecimal.valueOf(response.usage?.costDetails?.upstreamPromptCost ?: response.costDetails?.upstreamPromptCost ?: 0.0)),
        upstreamCompletionsCost = Cost(value = BigDecimal.valueOf(response.usage?.costDetails?.upstreamCompletionsCost ?: response.costDetails?.upstreamCompletionsCost ?: 0.0)),
    )
}
