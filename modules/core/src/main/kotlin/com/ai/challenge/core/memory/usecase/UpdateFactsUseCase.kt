package com.ai.challenge.core.memory.usecase

import arrow.core.Either
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.session.AgentSessionId

/**
 * Replace all facts for a session (replace-all semantics).
 * Application Use Case: delegates to FactMemoryProvider.replace().
 */
interface UpdateFactsUseCase {
    suspend fun execute(sessionId: AgentSessionId, facts: List<Fact>): Either<DomainError, Unit>
}
