package com.ai.challenge.core.context

/**
 * Value Object — role of a message in LLM context.
 *
 * [System] — system prompt (instructions, facts, summaries).
 * [User] — user's message.
 * [Assistant] — LLM's response.
 */
enum class MessageRole {
    System,
    User,
    Assistant,
}
