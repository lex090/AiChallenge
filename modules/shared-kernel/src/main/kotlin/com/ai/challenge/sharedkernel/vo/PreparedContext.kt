package com.ai.challenge.sharedkernel.vo

/**
 * Value Object -- result of context preparation for an LLM call.
 *
 * Output of [ContextManagerPort]. Contains the ordered
 * list of [ContextMessage] ready to send to LLM, plus metadata
 * about compression applied.
 *
 * Immutable. Created once per message send cycle.
 */
data class PreparedContext(
    val messages: List<ContextMessage>,
    val compressed: Boolean,
    val originalTurnCount: Int,
    val retainedTurnCount: Int,
    val summaryCount: Int,
)
