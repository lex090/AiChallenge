package com.ai.challenge.core.llm

import arrow.core.Either
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.error.DomainError

/**
 * Port — domain boundary for LLM interactions.
 *
 * Anti-Corruption Layer: domain services depend on this interface,
 * not on any specific LLM provider. Adapters in the data layer
 * implement this port and translate between domain types
 * and provider-specific APIs.
 */
interface LlmPort {
    suspend fun complete(
        messages: List<ContextMessage>,
        responseFormat: ResponseFormat,
    ): Either<DomainError, LlmResponse>
}
