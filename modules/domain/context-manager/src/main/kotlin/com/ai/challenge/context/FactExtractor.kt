package com.ai.challenge.context

import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.session.AgentSessionId

interface FactExtractor {
    suspend fun extract(
        sessionId: AgentSessionId,
        currentFacts: List<Fact>,
        newUserMessage: MessageContent,
        lastAssistantResponse: MessageContent?,
    ): List<Fact>
}
