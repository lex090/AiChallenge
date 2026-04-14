package com.ai.challenge.sharedkernel.vo

/**
 * Value Object -- system instructions provided by a Project.
 *
 * Prepended as a system message to every LLM request
 * for sessions belonging to the project. Contains
 * project-level constraints, specialization, or behavioral rules.
 *
 * Immutable. Equality by value.
 */
@JvmInline
value class SystemInstructions(val value: String)
