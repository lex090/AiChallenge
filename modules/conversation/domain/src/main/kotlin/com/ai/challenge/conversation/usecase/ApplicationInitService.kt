package com.ai.challenge.conversation.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.conversation.model.AgentSession
import com.ai.challenge.conversation.model.SessionTitle
import com.ai.challenge.conversation.service.SessionService
import com.ai.challenge.sharedkernel.error.DomainError

/**
 * Application Service -- application initialization.
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
