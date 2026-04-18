package com.ai.challenge.sharedkernel.port

import com.ai.challenge.sharedkernel.identity.UserId

/**
 * Port -- allows Context Management BC to read user preferences
 * from Conversation BC without creating a direct module dependency.
 *
 * Defined in shared-kernel so that external bounded contexts can fetch
 * the free-form preferences string for a given [UserId] without depending
 * on Conversation's aggregate internals.
 *
 * Returns null when no user exists for the given [UserId].
 *
 * Dependency direction: defined in shared-kernel,
 * implemented in conversation/data, consumed by context-management/domain.
 */
interface UserQueryPort {
    suspend fun getPreferences(userId: UserId): String?
}
