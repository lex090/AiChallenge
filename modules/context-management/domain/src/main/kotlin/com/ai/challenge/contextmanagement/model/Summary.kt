package com.ai.challenge.contextmanagement.model

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.vo.CreatedAt

/**
 * Value Object -- summarization result for a range of turns.
 *
 * Has no identity -- write-once, never updated.
 * Defined by [content] + turn range ([fromTurnIndex]..[toTurnIndex]).
 *
 * Not part of AgentSession aggregate -- internal state of
 * SummarizeOnThreshold strategy in Context Management context.
 *
 * [sessionId] is a correlation ID, not aggregate membership.
 *
 * Invariants:
 * - [fromTurnIndex] <= [toTurnIndex].
 * - [content] is never blank.
 * - Immutable after creation.
 */
data class Summary(
    val sessionId: AgentSessionId,
    val content: SummaryContent,
    val fromTurnIndex: TurnIndex,
    val toTurnIndex: TurnIndex,
    val createdAt: CreatedAt,
)
