package com.ai.challenge.contextmanagement.memory.impl

import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.MemoryService
import com.ai.challenge.contextmanagement.memory.MemoryType
import com.ai.challenge.contextmanagement.model.InstructionsContent
import com.ai.challenge.contextmanagement.model.UserPreferencesMemory
import com.ai.challenge.sharedkernel.event.DomainEvent
import com.ai.challenge.sharedkernel.event.DomainEventHandler
import com.ai.challenge.sharedkernel.port.UserQueryPort

/**
 * Event Handler -- upserts user preferences in CM memory
 * when a User's profile is updated in Conversation BC.
 *
 * Fetches the latest preferences via [UserQueryPort] and delegates
 * to [UserPreferencesMemoryProvider] for persistence. Returns early
 * (no-op) if the user has no preferences configured yet.
 */
class UserUpdatedHandler(
    private val memoryService: MemoryService,
    private val userQueryPort: UserQueryPort,
) : DomainEventHandler<DomainEvent.UserUpdated> {

    override suspend fun handle(event: DomainEvent.UserUpdated) {
        val preferences = userQueryPort.getPreferences(userId = event.userId) ?: return
        val provider = memoryService.provider(type = MemoryType.UserPreferences)
        val scope = MemoryScope.User(userId = event.userId)
        val memory = UserPreferencesMemory(
            userId = event.userId,
            content = InstructionsContent(value = preferences),
        )
        provider.save(scope = scope, preferences = memory)
    }
}
