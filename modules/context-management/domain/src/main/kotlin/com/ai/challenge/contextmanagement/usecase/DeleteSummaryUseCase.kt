package com.ai.challenge.contextmanagement.usecase

import arrow.core.Either
import com.ai.challenge.contextmanagement.model.Summary
import com.ai.challenge.sharedkernel.error.DomainError

/**
 * Application Use Case -- delete a specific summary from a session.
 *
 * Delegates to [SummaryMemoryProvider.delete].
 */
interface DeleteSummaryUseCase {
    suspend fun execute(summary: Summary): Either<DomainError, Unit>
}
