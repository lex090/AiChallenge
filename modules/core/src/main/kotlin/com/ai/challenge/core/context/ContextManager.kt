package com.ai.challenge.core.context

import com.ai.challenge.core.session.SessionId
import com.ai.challenge.core.turn.Turn

interface ContextManager {
    suspend fun prepareContext(
        sessionId: SessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext
}
