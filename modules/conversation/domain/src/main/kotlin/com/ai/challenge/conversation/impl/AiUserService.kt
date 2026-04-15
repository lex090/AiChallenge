package com.ai.challenge.conversation.impl

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.conversation.model.User
import com.ai.challenge.conversation.model.UserName
import com.ai.challenge.conversation.model.UserPreferences
import com.ai.challenge.conversation.repository.UserRepository
import com.ai.challenge.conversation.service.UserService
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.UserId
import com.ai.challenge.sharedkernel.vo.CreatedAt
import com.ai.challenge.sharedkernel.vo.UpdatedAt
import kotlin.time.Clock

/**
 * Domain Service implementation -- [User] lifecycle management.
 *
 * Creates, updates, deletes users. Raises [DomainError.UserNotFound]
 * when a lookup by [UserId] yields no result.
 */
class AiUserService(
    private val userRepository: UserRepository,
) : UserService {

    override suspend fun create(
        name: UserName,
        preferences: UserPreferences,
    ): Either<DomainError, User> = either {
        val now = Clock.System.now()
        val user = User(
            id = UserId.generate(),
            name = name,
            preferences = preferences,
            createdAt = CreatedAt(value = now),
            updatedAt = UpdatedAt(value = now),
        )
        userRepository.save(user = user)
    }

    override suspend fun get(id: UserId): Either<DomainError, User> = either {
        userRepository.get(id = id) ?: raise(DomainError.UserNotFound(id = id))
    }

    override suspend fun delete(id: UserId): Either<DomainError, Unit> = either {
        userRepository.get(id = id) ?: raise(DomainError.UserNotFound(id = id))
        userRepository.delete(id = id)
    }

    override suspend fun list(): Either<DomainError, List<User>> =
        Either.Right(value = userRepository.list())

    override suspend fun update(
        id: UserId,
        name: UserName,
        preferences: UserPreferences,
    ): Either<DomainError, User> = either {
        val user = userRepository.get(id = id) ?: raise(DomainError.UserNotFound(id = id))
        val updated = user
            .withUpdatedName(newName = name)
            .withUpdatedPreferences(newPreferences = preferences)
        userRepository.update(user = updated)
    }
}
