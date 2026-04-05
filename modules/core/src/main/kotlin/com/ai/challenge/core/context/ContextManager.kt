package com.ai.challenge.core.context

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn

interface ContextManager {
    suspend fun prepareContext(
        sessionId: AgentSessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext
}
