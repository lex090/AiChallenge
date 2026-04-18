package com.ai.challenge.contextmanagement.memory.impl

import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.MemoryService
import com.ai.challenge.sharedkernel.event.DomainEvent
import com.ai.challenge.sharedkernel.event.DomainEventHandler

/**
 * Event Handler -- cleans up project instructions from CM memory
 * when a project is deleted in Conversation BC.
 *
 * Delegates to [MemoryService.clearScope] with project scope,
 * which iterates all providers. Session-scoped providers
 * ignore project scope gracefully.
 */
class ProjectDeletedCleanupHandler(
    private val memoryService: MemoryService,
) : DomainEventHandler<DomainEvent.ProjectDeleted> {

    override suspend fun handle(event: DomainEvent.ProjectDeleted) {
        memoryService.clearScope(scope = MemoryScope.Project(projectId = event.projectId))
    }
}
