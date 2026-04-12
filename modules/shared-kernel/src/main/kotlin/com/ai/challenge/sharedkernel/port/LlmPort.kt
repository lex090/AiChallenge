package com.ai.challenge.sharedkernel.port

import arrow.core.Either
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.vo.ContextMessage
import com.ai.challenge.sharedkernel.vo.LlmResponse
import com.ai.challenge.sharedkernel.vo.ResponseFormat

/**
 * Port -- domain boundary for LLM interactions.
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
