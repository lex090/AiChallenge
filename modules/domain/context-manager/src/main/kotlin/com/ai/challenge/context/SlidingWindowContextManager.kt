package com.ai.challenge.context

import com.ai.challenge.core.CompressedContext
import com.ai.challenge.core.ContextManager
import com.ai.challenge.core.ContextMessage
import com.ai.challenge.core.MessageRole
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn

class SlidingWindowContextManager(
    private val windowSize: Int,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: SessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext {
        val retained = if (history.size > windowSize) {
            history.takeLast(windowSize)
        } else {
            history
        }

        val messages = buildList {
            for (turn in retained) {
                add(ContextMessage(MessageRole.User, turn.userMessage))
                add(ContextMessage(MessageRole.Assistant, turn.agentResponse))
            }
            add(ContextMessage(MessageRole.User, newMessage))
        }

        return CompressedContext(
            messages = messages,
            compressed = false,
            originalTurnCount = history.size,
            retainedTurnCount = retained.size,
            summaryCount = 0,
        )
    }
}
