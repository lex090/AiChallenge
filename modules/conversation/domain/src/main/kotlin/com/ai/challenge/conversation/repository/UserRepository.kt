package com.ai.challenge.conversation.repository

import com.ai.challenge.conversation.model.User
import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Repository -- sole access point to the [User] aggregate persistence.
 *
 * DDD rule: one repository per aggregate. User is a separate aggregate
 * from AgentSession and Project, so it has its own repository.
 */
interface UserRepository {
    suspend fun save(user: User): User
    suspend fun get(id: UserId): User?
    suspend fun delete(id: UserId)
    suspend fun list(): List<User>
    suspend fun update(user: User): User
}
