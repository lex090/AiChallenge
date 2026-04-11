package com.ai.challenge.core.llm

import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.usage.model.UsageRecord

/**
 * Value Object — result of an LLM completion request.
 *
 * Pairs the generated [content] with [usage] metrics.
 * Domain-level abstraction — no LLM provider details leak through.
 */
data class LlmResponse(
    val content: MessageContent,
    val usage: UsageRecord,
)
