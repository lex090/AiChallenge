package com.ai.challenge.conversation.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.conversation.service.UserService
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.event.DomainEvent
import com.ai.challenge.sharedkernel.event.DomainEventPublisher
import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Application Service -- delete user use case.
 *
 * Orchestrates:
 * 1. Deletes user via [UserService]
 * 2. Publishes [DomainEvent.UserDeleted] for Context Management cleanup
 */
class DeleteUserUseCase(
    private val userService: UserService,
    private val eventPublisher: DomainEventPublisher,
) {
    suspend fun execute(userId: UserId): Either<DomainError, Unit> = either {
        userService.delete(id = userId).bind()
        eventPublisher.publish(event = DomainEvent.UserDeleted(userId = userId))
    }
}
