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

/**
 * Application Service -- create user use case.
 *
 * Orchestrates user creation via [UserService] and publishes
 * [DomainEvent.UserCreated] for Context Management memory initialisation.
 */
class CreateUserUseCase(
    private val userService: UserService,
    private val eventPublisher: DomainEventPublisher,
) {
    suspend fun execute(
        name: UserName,
        preferences: UserPreferences,
    ): Either<DomainError, User> = either {
        val user = userService.create(name = name, preferences = preferences).bind()
        eventPublisher.publish(event = DomainEvent.UserCreated(userId = user.id, userName = user.name.value))
        user
    }
}
