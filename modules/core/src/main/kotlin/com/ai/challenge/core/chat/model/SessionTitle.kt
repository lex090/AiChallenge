package com.ai.challenge.core.chat.model

/**
 * Session title.
 * Value object instead of raw String — encapsulates validation rules.
 * Can be empty on creation (auto-generated from first message).
 */
@JvmInline
value class SessionTitle(val value: String)
