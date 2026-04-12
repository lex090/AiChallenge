package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.ContextStrategyConfig
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.context.PreparedContext
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryService
import com.ai.challenge.core.memory.MemoryType
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn

class StickyFactsStrategy(
    private val repository: AgentSessionRepository,
    private val memoryService: MemoryService,
    private val factExtractor: FactExtractor,
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
        val history = repository.getTurnsByBranch(branchId = branchId)
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
            withFacts(facts = updatedFacts, retainedTurns = retained, history = history, newMessage = newMessage)
        }
    }

    private fun withoutCompression(history: List<Turn>, newMessage: MessageContent): PreparedContext =
        PreparedContext(
            messages = turnsToMessages(turns = history) + ContextMessage(role = MessageRole.User, content = newMessage),
            compressed = false,
            originalTurnCount = history.size,
            retainedTurnCount = history.size,
            summaryCount = 0,
        )

    private fun withFacts(
        facts: List<Fact>,
        retainedTurns: List<Turn>,
        history: List<Turn>,
        newMessage: MessageContent,
    ): PreparedContext =
        PreparedContext(
            messages = factsMessages(facts = facts, retainedTurns = retainedTurns, newMessage = newMessage),
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = retainedTurns.size,
            summaryCount = 0,
        )

    private fun factsMessages(
        facts: List<Fact>,
        retainedTurns: List<Turn>,
        newMessage: MessageContent,
    ): List<ContextMessage> =
        buildList {
            add(ContextMessage(role = MessageRole.System, content = MessageContent(value = formatFacts(facts = facts))))
            addAll(turnsToMessages(turns = retainedTurns))
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
