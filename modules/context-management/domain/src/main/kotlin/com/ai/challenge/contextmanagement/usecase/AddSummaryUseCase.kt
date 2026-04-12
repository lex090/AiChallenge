package com.ai.challenge.contextmanagement.usecase

import arrow.core.Either
import com.ai.challenge.contextmanagement.model.Summary
import com.ai.challenge.sharedkernel.error.DomainError

/**
 * Application Use Case -- append a summary to a session.
 *
 * Implements append-only semantics. Delegates to [SummaryMemoryProvider.append].
 */
interface AddSummaryUseCase {
    suspend fun execute(summary: Summary): Either<DomainError, Unit>
}
