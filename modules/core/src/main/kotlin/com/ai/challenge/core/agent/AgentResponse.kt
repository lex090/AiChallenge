package com.ai.challenge.core.agent

import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.model.UsageRecord

data class AgentResponse(
    val text: MessageContent,
    val turnId: TurnId,
    val usage: UsageRecord,
)
