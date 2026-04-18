package com.ai.challenge.conversation.usecase

import arrow.core.Either
import com.ai.challenge.conversation.model.User
import com.ai.challenge.conversation.service.UserService
import com.ai.challenge.sharedkernel.error.DomainError

/**
 * Application Service -- list users use case.
 *
 * Returns all users for UI display.
 */
class ListUsersUseCase(
    private val userService: UserService,
) {
    suspend fun execute(): Either<DomainError, List<User>> =
        userService.list()
}
