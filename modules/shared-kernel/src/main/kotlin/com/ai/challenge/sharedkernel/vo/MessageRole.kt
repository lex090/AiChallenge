package com.ai.challenge.sharedkernel.vo

/**
 * Value Object -- role of a message in LLM context.
 *
 * [System] -- system prompt (instructions, facts, summaries).
 * [User] -- user's message.
 * [Assistant] -- LLM's response.
 */
enum class MessageRole {
    System,
    User,
    Assistant,
}
