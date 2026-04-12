package com.ai.challenge.contextmanagement.usecase.impl

import arrow.core.Either
import arrow.core.right
import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.MemoryService
import com.ai.challenge.contextmanagement.memory.MemoryType
import com.ai.challenge.contextmanagement.model.Fact
import com.ai.challenge.contextmanagement.usecase.UpdateFactsUseCase
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.AgentSessionId

/**
 * Default implementation of [UpdateFactsUseCase].
 *
 * Replaces all facts for a session via [MemoryService].
 */
class DefaultUpdateFactsUseCase(
    private val memoryService: MemoryService,
) : UpdateFactsUseCase {

    override suspend fun execute(sessionId: AgentSessionId, facts: List<Fact>): Either<DomainError, Unit> {
        val scope = MemoryScope.Session(sessionId = sessionId)
        memoryService.provider(type = MemoryType.Facts).replace(scope = scope, facts = facts)
        return Unit.right()
    }
}
