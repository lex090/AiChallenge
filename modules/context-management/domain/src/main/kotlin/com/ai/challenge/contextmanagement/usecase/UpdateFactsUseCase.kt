package com.ai.challenge.contextmanagement.usecase

import arrow.core.Either
import com.ai.challenge.contextmanagement.model.Fact
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.AgentSessionId

/**
 * Application Use Case -- replace all facts for a session.
 *
 * Implements replace-all semantics: deletes existing facts
 * and writes the new list. Delegates to [FactMemoryProvider.replace].
 */
interface UpdateFactsUseCase {
    suspend fun execute(sessionId: AgentSessionId, facts: List<Fact>): Either<DomainError, Unit>
}
