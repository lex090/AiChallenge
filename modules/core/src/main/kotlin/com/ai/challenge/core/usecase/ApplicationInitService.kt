package com.ai.challenge.core.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSession

/**
 * Application Service — application initialization.
 *
 * Contains application-level UX policies that are NOT domain rules:
 * - "At least one session always exists"
 * - Default session creation on first launch
 *
 * This is NOT a domain rule (domain doesn't care about 0 or 100 sessions).
 * It's a UX policy.
 */
class ApplicationInitService(
    private val createSessionUseCase: CreateSessionUseCase,
    private val sessionService: SessionService,
) {
    suspend fun ensureAtLeastOneSession(): Either<DomainError, AgentSession?> = either {
        val sessions = sessionService.list().bind()
        if (sessions.isEmpty()) {
            createSessionUseCase.execute(title = SessionTitle(value = "")).bind()
        } else {
            null
        }
    }
}
