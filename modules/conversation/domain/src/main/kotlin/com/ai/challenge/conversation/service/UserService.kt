package com.ai.challenge.conversation.service

import arrow.core.Either
import com.ai.challenge.conversation.model.User
import com.ai.challenge.conversation.model.UserName
import com.ai.challenge.conversation.model.UserPreferences
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Domain Service -- [User] lifecycle management.
 *
 * CRUD operations on users. On deletion, clears userId
 * from all associated sessions (they become free).
 *
 * Contains no own state -- all logic is stateless.
 */
interface UserService {
    suspend fun create(name: UserName, preferences: UserPreferences): Either<DomainError, User>
    suspend fun get(id: UserId): Either<DomainError, User>
    suspend fun delete(id: UserId): Either<DomainError, Unit>
    suspend fun list(): Either<DomainError, List<User>>
    suspend fun update(id: UserId, name: UserName, preferences: UserPreferences): Either<DomainError, User>
}
