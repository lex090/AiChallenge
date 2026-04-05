package com.ai.challenge.core

interface ContextManager {
    suspend fun prepareContext(
        sessionId: SessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext
}
