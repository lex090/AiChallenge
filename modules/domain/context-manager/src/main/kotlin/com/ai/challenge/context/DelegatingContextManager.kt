package com.ai.challenge.context

import com.ai.challenge.core.CompressedContext
import com.ai.challenge.core.ContextManager
import com.ai.challenge.core.ContextStrategyType
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn

class DelegatingContextManager(
    private val managers: Map<ContextStrategyType, ContextManager>,
    initialStrategy: ContextStrategyType = ContextStrategyType.SlidingWindow,
) : ContextManager {

    var activeStrategy: ContextStrategyType = initialStrategy

    override suspend fun prepareContext(
        sessionId: SessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext {
        val manager = managers[activeStrategy]
            ?: error("No ContextManager registered for strategy: $activeStrategy")
        return manager.prepareContext(sessionId, history, newMessage)
    }
}
