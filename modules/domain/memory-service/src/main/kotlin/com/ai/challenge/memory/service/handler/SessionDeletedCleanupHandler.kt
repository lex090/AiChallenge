package com.ai.challenge.memory.service.handler

import com.ai.challenge.core.event.DomainEvent
import com.ai.challenge.core.event.DomainEventHandler
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryService

/**
 * Event Handler — cleans up all memory data
 * when a session is deleted in Conversation Context.
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
