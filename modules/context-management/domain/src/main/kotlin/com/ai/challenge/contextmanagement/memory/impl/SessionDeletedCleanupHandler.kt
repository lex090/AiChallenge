package com.ai.challenge.contextmanagement.memory.impl

import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.MemoryService
import com.ai.challenge.sharedkernel.event.DomainEvent
import com.ai.challenge.sharedkernel.event.DomainEventHandler

/**
 * Event Handler -- cleans up all memory data
 * when a session is deleted in Conversation bounded context.
 *
 * Delegates to [MemoryService.clearScope] which iterates
 * all registered providers. Adding new memory types
 * requires no changes to this handler.
 */
class SessionDeletedCleanupHandler(
    private val memoryService: MemoryService,
) : DomainEventHandler<DomainEvent.SessionDeleted> {

    override suspend fun handle(event: DomainEvent.SessionDeleted) {
        memoryService.clearScope(scope = MemoryScope.Session(sessionId = event.sessionId))
    }
}
