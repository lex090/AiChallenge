package com.ai.challenge.core.memory.usecase

import arrow.core.Either
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.summary.Summary

/**
 * Delete a specific summary from a session.
 * Application Use Case: delegates to SummaryMemoryProvider.delete().
 */
interface DeleteSummaryUseCase {
    suspend fun execute(summary: Summary): Either<DomainError, Unit>
}
