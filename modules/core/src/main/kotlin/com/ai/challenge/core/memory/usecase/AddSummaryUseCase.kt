package com.ai.challenge.core.memory.usecase

import arrow.core.Either
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.summary.Summary

/**
 * Append a summary to a session (append-only semantics).
 * Application Use Case: delegates to SummaryMemoryProvider.append().
 */
interface AddSummaryUseCase {
    suspend fun execute(summary: Summary): Either<DomainError, Unit>
}
