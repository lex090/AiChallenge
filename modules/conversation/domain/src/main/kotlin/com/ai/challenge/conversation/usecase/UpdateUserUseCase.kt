package com.ai.challenge.conversation.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.conversation.model.User
import com.ai.challenge.conversation.model.UserName
import com.ai.challenge.conversation.model.UserPreferences
import com.ai.challenge.conversation.service.UserService
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.event.DomainEvent
import com.ai.challenge.sharedkernel.event.DomainEventPublisher
import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Application Service -- update user use case.
 *
 * Orchestrates user name and preferences update via [UserService]
 * and publishes [DomainEvent.UserUpdated] for downstream subscribers.
 */
class UpdateUserUseCase(
    private val userService: UserService,
    private val eventPublisher: DomainEventPublisher,
) {
    suspend fun execute(
        id: UserId,
        name: UserName,
        preferences: UserPreferences,
    ): Either<DomainError, User> = either {
        val user = userService.update(id = id, name = name, preferences = preferences).bind()
        eventPublisher.publish(event = DomainEvent.UserUpdated(userId = user.id))
        user
    }
}
