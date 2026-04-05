package com.ai.challenge.core

data class AgentResponse(
    val text: String,
    val turnId: TurnId,
    val tokenDetails: TokenDetails,
    val costDetails: CostDetails,
)
