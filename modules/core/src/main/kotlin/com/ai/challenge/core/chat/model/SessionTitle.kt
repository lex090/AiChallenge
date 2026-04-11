package com.ai.challenge.core.chat.model

/**
 * Value Object — title of an [AgentSession].
 *
 * Has no identity — defined only by the string it wraps.
 * Immutable. Can be empty at session creation (auto-generated
 * from first user message).
 */
@JvmInline
value class SessionTitle(val value: String)
