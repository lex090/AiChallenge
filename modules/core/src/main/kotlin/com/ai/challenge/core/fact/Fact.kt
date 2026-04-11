package com.ai.challenge.core.fact

import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.session.AgentSessionId

/**
 * Value Object — extracted fact from conversation.
 * No stable identity — facts are fully recreated on each message.
 */
data class Fact(
    val sessionId: AgentSessionId,
    val category: FactCategory,
    val key: FactKey,
    val value: FactValue,
)
