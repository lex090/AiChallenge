package com.ai.challenge.core.summary

import com.ai.challenge.core.context.model.SummaryContent
import com.ai.challenge.core.context.model.TurnIndex
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt

/**
 * Value Object — summarization result for a range of turns.
 *
 * Has no identity — write-once, never updated.
 * Defined by [content] + turn range ([fromTurnIndex]..[toTurnIndex]).
 *
 * Not part of [AgentSession] aggregate — internal state of
 * SummarizeOnThreshold strategy in Context Management context.
 *
 * [sessionId] is a correlation ID, not aggregate membership.
 */
data class Summary(
    val sessionId: AgentSessionId,
    val content: SummaryContent,
    val fromTurnIndex: TurnIndex,
    val toTurnIndex: TurnIndex,
    val createdAt: CreatedAt,
)
