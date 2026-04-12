package com.ai.challenge.contextmanagement.usecase.impl

import arrow.core.Either
import arrow.core.right
import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.MemoryService
import com.ai.challenge.contextmanagement.memory.MemoryType
import com.ai.challenge.contextmanagement.model.Summary
import com.ai.challenge.contextmanagement.usecase.AddSummaryUseCase
import com.ai.challenge.sharedkernel.error.DomainError

/**
 * Default implementation of [AddSummaryUseCase].
 *
 * Appends a summary to a session via [MemoryService].
 */
class DefaultAddSummaryUseCase(
    private val memoryService: MemoryService,
) : AddSummaryUseCase {

    override suspend fun execute(summary: Summary): Either<DomainError, Unit> {
        val scope = MemoryScope.Session(sessionId = summary.sessionId)
        memoryService.provider(type = MemoryType.Summaries).append(scope = scope, summary = summary)
        return Unit.right()
    }
}
