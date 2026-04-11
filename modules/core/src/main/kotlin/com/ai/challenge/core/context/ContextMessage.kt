package com.ai.challenge.core.context

import com.ai.challenge.core.chat.model.MessageContent

/**
 * Value Object — a single message in the prepared LLM context.
 *
 * Building block for [PreparedContext]. Pairs [MessageRole]
 * with [MessageContent] to represent system prompts,
 * user messages, and assistant responses.
 */
data class ContextMessage(
    val role: MessageRole,
    val content: MessageContent,
)
