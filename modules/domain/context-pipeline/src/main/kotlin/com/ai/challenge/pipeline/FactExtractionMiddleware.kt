package com.ai.challenge.pipeline

import com.ai.challenge.core.ContextMiddleware
import com.ai.challenge.core.ContextState
import com.ai.challenge.core.FactExtractor
import com.ai.challenge.core.FactRepository

class FactExtractionMiddleware(
    private val factExtractor: FactExtractor,
    private val factRepository: FactRepository,
) : ContextMiddleware {
    override suspend fun process(
        state: ContextState,
        next: suspend (ContextState) -> ContextState,
    ): ContextState {
        val currentFacts = factRepository.getBySession(state.sessionId)
        val updated = factExtractor.extract(state.history, currentFacts, state.newMessage)
        factRepository.save(state.sessionId, updated)
        return next(state.copy(facts = updated))
    }
}
