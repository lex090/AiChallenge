package com.ai.challenge.core.context

import com.ai.challenge.core.session.AgentSessionId

interface ContextManager {
    suspend fun prepareContext(
        sessionId: AgentSessionId,
        newMessage: String,
    ): PreparedContext

    data class PreparedContext(
        val messages: List<ContextMessage>,
        val compressed: Boolean,
        val originalTurnCount: Int,
        val retainedTurnCount: Int,
        val summaryCount: Int,
    ) {
        data class ContextMessage(
            val role: MessageRole,
            val content: String,
        ) {
            enum class MessageRole {
                System,
                User,
                Assistant,
            }
        }
    }
}
