package com.ai.challenge.agent

import com.ai.challenge.session.RequestMetrics
import com.ai.challenge.session.TurnId

data class AgentResponse(
    val text: String,
    val turnId: TurnId,
    val metrics: RequestMetrics,
)
