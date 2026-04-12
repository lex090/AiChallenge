package com.ai.challenge.contextmanagement.usecase.impl

import arrow.core.Either
import arrow.core.right
import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.MemoryService
import com.ai.challenge.contextmanagement.memory.MemoryType
import com.ai.challenge.contextmanagement.model.Summary
import com.ai.challenge.contextmanagement.usecase.DeleteSummaryUseCase
import com.ai.challenge.sharedkernel.error.DomainError

/**
 * Default implementation of [DeleteSummaryUseCase].
 *
 * Deletes a specific summary from a session via [MemoryService].
 */
class DefaultDeleteSummaryUseCase(
    private val memoryService: MemoryService,
) : DeleteSummaryUseCase {

    override suspend fun execute(summary: Summary): Either<DomainError, Unit> {
        val scope = MemoryScope.Session(sessionId = summary.sessionId)
        memoryService.provider(type = MemoryType.Summaries).delete(scope = scope, summary = summary)
        return Unit.right()
    }
}
