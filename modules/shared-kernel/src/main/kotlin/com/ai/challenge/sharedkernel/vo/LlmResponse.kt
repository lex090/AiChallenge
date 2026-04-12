package com.ai.challenge.sharedkernel.vo

/**
 * Value Object -- result of an LLM completion request.
 *
 * Pairs the generated [content] with [usage] metrics.
 * Domain-level abstraction -- no LLM provider details leak through.
 *
 * Uses [LlmUsage] (lightweight token counts) instead of Conversation's
 * UsageRecord to avoid coupling shared kernel to Conversation-specific
 * cost and detailed token breakdown types.
 */
data class LlmResponse(
    val content: MessageContent,
    val usage: LlmUsage,
)
