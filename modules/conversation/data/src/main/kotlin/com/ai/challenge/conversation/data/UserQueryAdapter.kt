package com.ai.challenge.conversation.data

import com.ai.challenge.conversation.repository.UserRepository
import com.ai.challenge.sharedkernel.identity.UserId
import com.ai.challenge.sharedkernel.port.UserQueryPort

/**
 * Adapter -- implements [UserQueryPort] by delegating to [UserRepository].
 *
 * Bridges Conversation BC's User aggregate with the shared-kernel port
 * consumed by Context Management BC. Maps the domain [User.preferences]
 * value object to a plain [String] as required by the port contract.
 *
 * Returns null when no [User] is found for the given [UserId].
 */
class UserQueryAdapter(
    private val userRepository: UserRepository,
) : UserQueryPort {

    override suspend fun getPreferences(userId: UserId): String? {
        val user = userRepository.get(id = userId) ?: return null
        return user.preferences.value
    }
}
