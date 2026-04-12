package com.ai.challenge.conversation.model

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.TurnId
import com.ai.challenge.sharedkernel.vo.CreatedAt
import com.ai.challenge.sharedkernel.vo.MessageContent

/**
 * Entity -- single exchange (user message + assistant response)
 * within aggregate [AgentSession]. Immutable.
 *
 * Has stable identity [TurnId] -- branches reference turns,
 * UI queries metrics by turn. Two turns with identical text
 * are different turns (entity semantics, not value semantics).
 *
 * Created once during request processing and never modified (write-once).
 *
 * Lifecycle: created when user sends a message and receives a response.
 * Deleted cascadingly when session is deleted.
 *
 * [usage] -- embedded [UsageRecord] value object with token/cost metrics.
 * Part of Turn because it is created simultaneously, never changes,
 * and has no independent lifecycle or identity.
 *
 * [sessionId] -- reference to parent aggregate root. Deliberate compromise
 * for query-performance in relational DB. In strict DDD, child entities
 * don't store root ID, but here it simplifies repository queries.
 */
data class Turn(
    val id: TurnId,
    val sessionId: AgentSessionId,
    val userMessage: MessageContent,
    val assistantMessage: MessageContent,
    val usage: UsageRecord,
    val createdAt: CreatedAt,
)
