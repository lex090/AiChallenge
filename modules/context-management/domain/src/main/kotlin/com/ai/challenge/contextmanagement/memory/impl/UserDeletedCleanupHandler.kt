package com.ai.challenge.contextmanagement.memory.impl

import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.MemoryService
import com.ai.challenge.sharedkernel.event.DomainEvent
import com.ai.challenge.sharedkernel.event.DomainEventHandler

/**
 * Event Handler -- cleans up all user-scoped memory data
 * when a User is deleted in Conversation BC.
 *
 * Delegates to [MemoryService.clearScope] which iterates
 * all registered providers. Adding new user memory types
 * requires no changes to this handler.
 */
class UserDeletedCleanupHandler(
    private val memoryService: MemoryService,
) : DomainEventHandler<DomainEvent.UserDeleted> {

    override suspend fun handle(event: DomainEvent.UserDeleted) {
        memoryService.clearScope(scope = MemoryScope.User(userId = event.userId))
    }
}
