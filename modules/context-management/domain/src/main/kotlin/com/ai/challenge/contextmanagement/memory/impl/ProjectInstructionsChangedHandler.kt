package com.ai.challenge.contextmanagement.memory.impl

import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.MemoryService
import com.ai.challenge.contextmanagement.memory.MemoryType
import com.ai.challenge.contextmanagement.memory.ProjectInstructionsMemoryProvider
import com.ai.challenge.contextmanagement.model.InstructionsContent
import com.ai.challenge.contextmanagement.model.ProjectInstructions
import com.ai.challenge.sharedkernel.event.DomainEvent
import com.ai.challenge.sharedkernel.event.DomainEventHandler

/**
 * Event Handler -- upserts project instructions in CM memory
 * when instructions are created or updated in Conversation BC.
 *
 * Delegates to [ProjectInstructionsMemoryProvider] for persistence.
 */
class ProjectInstructionsChangedHandler(
    private val memoryService: MemoryService,
) : DomainEventHandler<DomainEvent.ProjectInstructionsChanged> {

    override suspend fun handle(event: DomainEvent.ProjectInstructionsChanged) {
        val provider = memoryService.provider(type = MemoryType.ProjectInstructions)
        val scope = MemoryScope.Project(projectId = event.projectId)
        val instructions = ProjectInstructions(
            projectId = event.projectId,
            content = InstructionsContent(value = event.instructions.value),
        )
        provider.save(scope = scope, instructions = instructions)
    }
}
