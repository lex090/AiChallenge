package com.ai.challenge.core.agent

import com.ai.challenge.core.metrics.CostDetails
import com.ai.challenge.core.metrics.TokenDetails
import com.ai.challenge.core.turn.TurnId

data class AgentResponse(
    val text: String,
    val turnId: TurnId,
    val tokenDetails: TokenDetails,
    val costDetails: CostDetails,
)
