package com.ai.challenge.sharedkernel.vo

/**
 * Value Object -- opaque identifier for a context management mode.
 *
 * Replaces the concrete ContextManagementType enum in the shared kernel.
 * Conversation bounded context stores this opaque ID without knowing
 * which strategies exist. Context Management bounded context owns the
 * actual enum and validates [ContextModeId] values via [ContextModeValidatorPort].
 *
 * This decouples Conversation from Context Management: adding a new
 * strategy requires no changes in Conversation's domain model.
 *
 * Invariants:
 * - [value] must be a non-blank string matching one of the modes
 *   registered in Context Management bounded context.
 */
@JvmInline
value class ContextModeId(val value: String)
