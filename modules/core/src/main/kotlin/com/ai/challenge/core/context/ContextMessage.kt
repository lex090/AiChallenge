package com.ai.challenge.core.context

import com.ai.challenge.core.chat.model.MessageContent

data class ContextMessage(
    val role: MessageRole,
    val content: MessageContent,
)
