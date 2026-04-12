package com.ai.challenge.infrastructure.llm

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.ai.challenge.infrastructure.llm.model.ChatResponse
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.port.LlmPort
import com.ai.challenge.sharedkernel.vo.ContextMessage
import com.ai.challenge.sharedkernel.vo.LlmResponse
import com.ai.challenge.sharedkernel.vo.LlmUsage
import com.ai.challenge.sharedkernel.vo.MessageContent
import com.ai.challenge.sharedkernel.vo.MessageRole
import com.ai.challenge.sharedkernel.vo.ResponseFormat

/**
 * Adapter -- translates between domain [LlmPort] and OpenRouter API.
 *
 * Anti-Corruption Layer implementation: maps domain [ContextMessage]
 * and [ResponseFormat] to OpenRouter DSL calls, and maps
 * [ChatResponse] back to domain [LlmResponse] with [LlmUsage].
 */
class OpenRouterAdapter(
    private val openRouterService: OpenRouterService,
    private val model: String,
) : LlmPort {

    override suspend fun complete(
        messages: List<ContextMessage>,
        responseFormat: ResponseFormat,
    ): Either<DomainError, LlmResponse> = either {
        val response = catch({
            openRouterService.chat(model = model) {
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
        }) { e: Exception ->
            raise(DomainError.NetworkError(message = e.message ?: "Unknown network error"))
        }

        ensure(response.error == null) {
            DomainError.ApiError(message = response.error?.message ?: "Unknown API error")
        }

        val text = response.choices.firstOrNull()?.message?.content
        ensureNotNull(text) {
            DomainError.ApiError(message = "Empty response from LLM")
        }

        LlmResponse(
            content = MessageContent(value = text),
            usage = mapUsage(response = response),
        )
    }

    private fun mapUsage(response: ChatResponse): LlmUsage = LlmUsage(
        promptTokens = response.usage?.promptTokens ?: 0,
        completionTokens = response.usage?.completionTokens ?: 0,
        totalTokens = response.usage?.totalTokens ?: 0,
    )
}
