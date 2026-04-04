package com.ai.challenge.agent

import com.ai.challenge.session.TokenUsage

data class AgentResponse(
    val text: String,
    val tokenUsage: TokenUsage,
)
