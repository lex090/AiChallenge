package com.ai.challenge.core.fact

import com.ai.challenge.core.session.AgentSessionId

data class Fact(
    val id: FactId,
    val sessionId: AgentSessionId,
    val category: FactCategory,
    val key: String,
    val value: String,
)
