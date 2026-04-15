package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.MemoryService
import com.ai.challenge.contextmanagement.memory.MemoryType
import com.ai.challenge.contextmanagement.model.ContextManagementType
import com.ai.challenge.contextmanagement.model.ContextStrategyConfig
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.ai.challenge.sharedkernel.identity.UserId
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
 * Reads user-level memory (preferences, facts, notes) and project instructions
 * from CM memory via [MemoryService] and prepends them as system messages when available.
 * Final message order: user messages + project messages + strategy-prepared messages.
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
        userId: UserId?,
    ): PreparedContext {
        val type = ContextManagementType.fromModeId(contextModeId = contextModeId)
            ?: error("Unknown context mode: ${contextModeId.value}")
        val strategy = strategies[type] ?: error("No strategy for: $type")
        val config = configs[type] ?: error("No config for: $type")
        val prepared = strategy.prepare(sessionId = sessionId, branchId = branchId, newMessage = newMessage, config = config)

        val userMessages = buildUserContextMessages(userId = userId)
        val projectMessages = buildProjectContextMessages(projectId = projectId)

        return prepared.copy(messages = userMessages + projectMessages + prepared.messages)
    }

    private suspend fun buildUserContextMessages(userId: UserId?): List<ContextMessage> {
        if (userId == null) return emptyList()
        val scope = MemoryScope.User(userId = userId)
        val messages = mutableListOf<ContextMessage>()

        val preferences = memoryService.provider(type = MemoryType.UserPreferences).get(scope = scope)
        if (preferences != null && preferences.content.value.isNotBlank()) {
            messages.add(
                ContextMessage(
                    role = MessageRole.System,
                    content = MessageContent(value = "[User Preferences]\n${preferences.content.value}\n[/User Preferences]"),
                ),
            )
        }

        val facts = memoryService.provider(type = MemoryType.UserFacts).get(scope = scope)
        if (facts.isNotEmpty()) {
            val factsText = facts.joinToString(separator = "\n") { "- ${it.category.name}/${it.key.value}: ${it.value.value}" }
            messages.add(
                ContextMessage(
                    role = MessageRole.System,
                    content = MessageContent(value = "[User Facts]\n$factsText\n[/User Facts]"),
                ),
            )
        }

        val notes = memoryService.provider(type = MemoryType.UserNotes).get(scope = scope)
        if (notes.isNotEmpty()) {
            val notesText = notes.joinToString(separator = "\n\n") { "### ${it.title.value}\n${it.content.value}" }
            messages.add(
                ContextMessage(
                    role = MessageRole.System,
                    content = MessageContent(value = "[User Notes]\n$notesText\n[/User Notes]"),
                ),
            )
        }

        return messages
    }

    private suspend fun buildProjectContextMessages(projectId: ProjectId?): List<ContextMessage> {
        if (projectId == null) return emptyList()
        val instructions = memoryService
            .provider(type = MemoryType.ProjectInstructions)
            .get(scope = MemoryScope.Project(projectId = projectId))
            ?: return emptyList()
        if (instructions.content.value.isBlank()) return emptyList()
        return listOf(
            ContextMessage(
                role = MessageRole.System,
                content = MessageContent(value = "[Project Instructions]\n${instructions.content.value}\n[/Project Instructions]"),
            ),
        )
    }
}
