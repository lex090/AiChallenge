package com.ai.challenge.core

class ContextPipeline(private val middlewares: List<ContextMiddleware>) {
    suspend fun execute(initialState: ContextState): ContextState {
        val chain = middlewares.foldRight<ContextMiddleware, suspend (ContextState) -> ContextState>(
            { state -> state }
        ) { middleware, next ->
            { state -> middleware.process(state, next) }
        }
        return chain(initialState)
    }
}
