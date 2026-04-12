package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.MemoryService
import com.ai.challenge.contextmanagement.memory.MemoryType
import com.ai.challenge.contextmanagement.model.ContextStrategyConfig
import com.ai.challenge.contextmanagement.model.Fact
import com.ai.challenge.contextmanagement.model.FactCategory
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.port.TurnQueryPort
import com.ai.challenge.sharedkernel.vo.ContextMessage
import com.ai.challenge.sharedkernel.vo.MessageContent
import com.ai.challenge.sharedkernel.vo.MessageRole
import com.ai.challenge.sharedkernel.vo.PreparedContext
import com.ai.challenge.sharedkernel.vo.TurnSnapshot

/**
 * Strategy -- extract facts via LLM, retain with recent turns.
 *
 * Uses [FactExtractorPort] to maintain a set of key facts extracted
 * from the conversation. Recent turns are kept alongside the facts
 * as context. Used with [ContextManagementType.StickyFacts].
 */
class StickyFactsStrategy(
    private val turnQueryPort: TurnQueryPort,
    private val memoryService: MemoryService,
    private val factExtractor: FactExtractorPort,
) : ContextStrategy {

    override suspend fun prepare(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        config: ContextStrategyConfig,
    ): PreparedContext {
        val stickyConfig = config as ContextStrategyConfig.StickyFacts

        val factProvider = memoryService.provider(type = MemoryType.Facts)
        val scope = MemoryScope.Session(sessionId = sessionId)

        val currentFacts = factProvider.get(scope = scope)
        val history = turnQueryPort.getTurnSnapshots(sessionId = sessionId, branchId = branchId)
        val lastAssistantResponse = history.lastOrNull()?.assistantMessage

        val updatedFacts = factExtractor.extract(
            sessionId = sessionId,
            currentFacts = currentFacts,
            newUserMessage = newMessage,
            lastAssistantResponse = lastAssistantResponse,
        )
        if (updatedFacts.isEmpty()) {
            factProvider.clear(scope = scope)
        } else {
            factProvider.replace(scope = scope, facts = updatedFacts)
        }

        val retained = if (history.size > stickyConfig.retainLastTurns) {
            history.subList(history.size - stickyConfig.retainLastTurns, history.size)
        } else {
            history
        }

        return if (updatedFacts.isEmpty()) {
            withoutCompression(history = retained, newMessage = newMessage).copy(
                originalTurnCount = history.size,
            )
        } else {
            withFacts(facts = updatedFacts, retainedSnapshots = retained, history = history, newMessage = newMessage)
        }
    }

    private fun withoutCompression(history: List<TurnSnapshot>, newMessage: MessageContent): PreparedContext =
        PreparedContext(
            messages = snapshotsToMessages(snapshots = history) + ContextMessage(role = MessageRole.User, content = newMessage),
            compressed = false,
            originalTurnCount = history.size,
            retainedTurnCount = history.size,
            summaryCount = 0,
        )

    private fun withFacts(
        facts: List<Fact>,
        retainedSnapshots: List<TurnSnapshot>,
        history: List<TurnSnapshot>,
        newMessage: MessageContent,
    ): PreparedContext =
        PreparedContext(
            messages = factsMessages(facts = facts, retainedSnapshots = retainedSnapshots, newMessage = newMessage),
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = retainedSnapshots.size,
            summaryCount = 0,
        )

    private fun factsMessages(
        facts: List<Fact>,
        retainedSnapshots: List<TurnSnapshot>,
        newMessage: MessageContent,
    ): List<ContextMessage> =
        buildList {
            add(ContextMessage(role = MessageRole.System, content = MessageContent(value = formatFacts(facts = facts))))
            addAll(snapshotsToMessages(snapshots = retainedSnapshots))
            add(ContextMessage(role = MessageRole.User, content = newMessage))
        }

    private fun formatFacts(facts: List<Fact>): String {
        val grouped = facts.groupBy { it.category }
        return buildString {
            appendLine("You have the following context about this conversation:")
            appendLine()
            appendCategoryIfPresent(grouped = grouped, category = FactCategory.Goal, header = "## Goals")
            appendCategoryIfPresent(grouped = grouped, category = FactCategory.Constraint, header = "## Constraints")
            appendCategoryIfPresent(grouped = grouped, category = FactCategory.Preference, header = "## Preferences")
            appendCategoryIfPresent(grouped = grouped, category = FactCategory.Decision, header = "## Decisions")
            appendCategoryIfPresent(grouped = grouped, category = FactCategory.Agreement, header = "## Agreements")
        }.trimEnd()
    }

    private fun StringBuilder.appendCategoryIfPresent(
        grouped: Map<FactCategory, List<Fact>>,
        category: FactCategory,
        header: String,
    ) {
        val categoryFacts = grouped[category] ?: return
        appendLine(header)
        for (fact in categoryFacts) {
            appendLine("- ${fact.key.value}: ${fact.value.value}")
        }
        appendLine()
    }
}
