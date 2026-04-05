package com.ai.challenge.core

fun interface ContextMiddleware {
    suspend fun process(state: ContextState, next: suspend (ContextState) -> ContextState): ContextState
}
