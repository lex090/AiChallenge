package com.ai.challenge.contextmanagement.model

/**
 * Value Object -- text content of project instructions.
 *
 * Contains the system instructions configured for a project.
 * Has no identity -- defined solely by its string [value].
 */
@JvmInline
value class InstructionsContent(val value: String)
