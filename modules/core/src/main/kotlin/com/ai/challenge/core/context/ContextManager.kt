package com.ai.challenge.core.context

import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.session.AgentSessionId

interface ContextManager {
    suspend fun prepareContext(
        sessionId: AgentSessionId,
        newMessage: MessageContent,
    ): PreparedContext
}
