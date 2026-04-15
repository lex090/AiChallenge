package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.MemoryService
import com.ai.challenge.contextmanagement.memory.MemoryType
import com.ai.challenge.contextmanagement.model.ContextManagementType
import com.ai.challenge.contextmanagement.model.ContextStrategyConfig
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.ai.challenge.sharedkernel.port.ContextManagerPort
import com.ai.challenge.sharedkernel.vo.ContextMessage
import com.ai.challenge.sharedkernel.vo.ContextModeId
import com.ai.challenge.sharedkernel.vo.MessageContent
import com.ai.challenge.sharedkernel.vo.MessageRole
import com.ai.challenge.sharedkernel.vo.PreparedContext

/**
 * Adapter -- implements [ContextManagerPort] from the shared kernel.
 *
 * Orchestrates context preparation by mapping [ContextModeId] to
 * [ContextManagementType], selecting the corresponding [ContextStrategy]
 * and [ContextStrategyConfig], and delegating the actual preparation.
 *
 * Reads project instructions from CM memory via [MemoryService]
 * and prepends them as a system message when available.
 */
class ContextPreparationAdapter(
    private val strategies: Map<ContextManagementType, ContextStrategy>,
    private val configs: Map<ContextManagementType, ContextStrategyConfig>,
    private val memoryService: MemoryService,
) : ContextManagerPort {

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        contextModeId: ContextModeId,
        projectId: ProjectId?,
    ): PreparedContext {
        val type = ContextManagementType.fromModeId(contextModeId = contextModeId)
            ?: error("Unknown context mode: ${contextModeId.value}")
        val strategy = strategies[type] ?: error("No strategy for: $type")
        val config = configs[type] ?: error("No config for: $type")
        val prepared = strategy.prepare(sessionId = sessionId, branchId = branchId, newMessage = newMessage, config = config)

        if (projectId == null) {
            return prepared
        }

        val instructions = memoryService
            .provider(type = MemoryType.ProjectInstructions)
            .get(scope = MemoryScope.Project(projectId = projectId))
            ?: return prepared

        if (instructions.content.value.isBlank()) {
            return prepared
        }

        val systemMessage = ContextMessage(
            role = MessageRole.System,
            content = MessageContent(value = "[Project Instructions]\n${instructions.content.value}\n[/Project Instructions]"),
        )
        return prepared.copy(messages = listOf(systemMessage) + prepared.messages)
    }
}
