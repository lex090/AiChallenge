package com.ai.challenge.pipeline

import com.ai.challenge.core.ContextMiddleware
import com.ai.challenge.core.ContextState

class SlidingWindowMiddleware(private val windowSize: Int = 10) : ContextMiddleware {
    override suspend fun process(
        state: ContextState,
        next: suspend (ContextState) -> ContextState,
    ): ContextState {
        val trimmed = state.copy(history = state.history.takeLast(windowSize))
        return next(trimmed)
    }
}
