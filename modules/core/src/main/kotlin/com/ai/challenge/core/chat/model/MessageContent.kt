package com.ai.challenge.core.chat.model

/**
 * Message content.
 * Value object instead of String — single type for user and assistant messages.
 * Makes function signatures self-documenting:
 * send(sessionId, message: MessageContent) vs send(sessionId, message: String).
 */
@JvmInline
value class MessageContent(val value: String)
