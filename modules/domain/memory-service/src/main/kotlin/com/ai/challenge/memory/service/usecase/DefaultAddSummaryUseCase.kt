package com.ai.challenge.memory.service.usecase

import arrow.core.Either
import arrow.core.right
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryService
import com.ai.challenge.core.memory.MemoryType
import com.ai.challenge.core.memory.usecase.AddSummaryUseCase
import com.ai.challenge.core.summary.Summary

/**
 * Default implementation of [AddSummaryUseCase].
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
