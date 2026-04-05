package com.ai.challenge.pipeline

import com.ai.challenge.core.CompressedContext
import com.ai.challenge.core.ContextManager
import com.ai.challenge.core.ContextPipeline
import com.ai.challenge.core.ContextState
import com.ai.challenge.core.ContextStrategyType
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn

class PipelineContextManager(
    private val pipelines: Map<ContextStrategyType, ContextPipeline>,
) : ContextManager {

    var activeStrategy: ContextStrategyType = ContextStrategyType.SlidingWindow

    override suspend fun prepareContext(
        sessionId: SessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext {
        val pipeline = pipelines.getValue(activeStrategy)
        val state = ContextState(sessionId, history, newMessage)
        val result = pipeline.execute(state)
        return CompressedContext(
            messages = result.messages,
            compressed = false,
            originalTurnCount = history.size,
            retainedTurnCount = result.history.size,
            summaryCount = 0,
        )
    }
}
