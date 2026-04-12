package com.ai.challenge.sharedkernel.vo

/**
 * Value Object -- text content of a message (user or assistant).
 *
 * Has no identity -- defined only by the string it wraps.
 * Immutable. Single type for both user and assistant messages,
 * making function signatures self-documenting:
 * `send(message: MessageContent)` vs `send(message: String)`.
 */
@JvmInline
value class MessageContent(val value: String)
