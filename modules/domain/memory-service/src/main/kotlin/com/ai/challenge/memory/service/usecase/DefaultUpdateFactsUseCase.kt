package com.ai.challenge.memory.service.usecase

import arrow.core.Either
import arrow.core.right
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryService
import com.ai.challenge.core.memory.MemoryType
import com.ai.challenge.core.memory.usecase.UpdateFactsUseCase
import com.ai.challenge.core.session.AgentSessionId

/**
 * Default implementation of [UpdateFactsUseCase].
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
