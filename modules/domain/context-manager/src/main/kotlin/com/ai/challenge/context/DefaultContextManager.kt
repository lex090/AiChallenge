package com.ai.challenge.context

import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.context.PreparedContext
import com.ai.challenge.core.context.model.SummaryContent
import com.ai.challenge.core.context.model.TurnIndex
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.turn.Turn
import kotlin.time.Clock

class DefaultContextManager(
    private val repository: AgentSessionRepository,
    private val compressor: ContextCompressor,
    private val summaryRepository: SummaryRepository,
    private val factExtractor: FactExtractor,
    private val factRepository: FactRepository,
    private val branchingContextManager: BranchingContextManager,
) : ContextManager {

    private companion object {
        const val WINDOW_SIZE = 10
    }

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        newMessage: MessageContent,
    ): PreparedContext {
        val session = repository.get(id = sessionId)
            ?: error("Session not found: ${sessionId.value}")
        val type = session.contextManagementType

        return when (type) {
            is ContextManagementType.None -> passThrough(sessionId = sessionId, newMessage = newMessage)
            is ContextManagementType.SummarizeOnThreshold -> summarizeOnThreshold(
                sessionId = sessionId,
                newMessage = newMessage,
            )
            is ContextManagementType.Branching -> branchingContextManager.prepareContext(
                sessionId = sessionId,
                newMessage = newMessage,
            )
            is ContextManagementType.SlidingWindow -> slidingWindow(
                sessionId = sessionId,
                newMessage = newMessage,
            )
            is ContextManagementType.StickyFacts -> stickyFacts(sessionId = sessionId, newMessage = newMessage)
        }
    }

    // --- orchestration (side effects at boundaries) ---

    private suspend fun passThrough(
        sessionId: AgentSessionId,
        newMessage: MessageContent,
    ): PreparedContext {
        val history = repository.getTurns(sessionId = sessionId, limit = null)
        return withoutCompression(history = history, newMessage = newMessage)
    }

    private suspend fun summarizeOnThreshold(
        sessionId: AgentSessionId,
        newMessage: MessageContent,
    ): PreparedContext {
        val maxTurns = 15
        val retainLast = 5
        val compressionInterval = 10

        val history = repository.getTurns(sessionId = sessionId, limit = null)

        if (history.size < maxTurns) {
            return withoutCompression(history = history, newMessage = newMessage)
        }

        val lastSummary = summaryRepository.getBySession(sessionId = sessionId).maxByOrNull { it.toTurnIndex.value }

        if (lastSummary != null) {
            val turnsSinceLastSummary = history.size - lastSummary.toTurnIndex.value
            if (turnsSinceLastSummary < retainLast + compressionInterval) {
                return withExistingSummary(summary = lastSummary, history = history, newMessage = newMessage)
            }
        }

        val splitAt = (history.size - retainLast).coerceAtLeast(minimumValue = 0)
        val summaryContent = compressTurns(history = history, splitAt = splitAt, lastSummary = lastSummary)
        saveSummary(sessionId = sessionId, summaryContent = summaryContent, toTurnIndex = splitAt)
        return withNewSummary(summaryContent = summaryContent, history = history, splitAt = splitAt, newMessage = newMessage)
    }

    private suspend fun slidingWindow(
        sessionId: AgentSessionId,
        newMessage: MessageContent,
    ): PreparedContext {
        val history = repository.getTurns(sessionId = sessionId, limit = null)
        val windowed = history.takeLast(n = WINDOW_SIZE)
        return PreparedContext(
            messages = turnsToMessages(turns = windowed) + ContextMessage(role = MessageRole.User, content = newMessage),
            compressed = false,
            originalTurnCount = history.size,
            retainedTurnCount = windowed.size,
            summaryCount = 0,
        )
    }

    private suspend fun stickyFacts(
        sessionId: AgentSessionId,
        newMessage: MessageContent,
    ): PreparedContext {
        val retainLast = 5

        val currentFacts = factRepository.getBySession(sessionId = sessionId)
        val history = repository.getTurns(sessionId = sessionId, limit = null)
        val lastAssistantResponse = history.lastOrNull()?.assistantMessage

        val updatedFacts = factExtractor.extract(
            sessionId = sessionId,
            currentFacts = currentFacts,
            newUserMessage = newMessage,
            lastAssistantResponse = lastAssistantResponse,
        )
        if (updatedFacts.isEmpty()) {
            factRepository.deleteBySession(sessionId = sessionId)
        } else {
            factRepository.save(sessionId = sessionId, facts = updatedFacts)
        }

        val retained = if (history.size > retainLast) {
            history.subList(history.size - retainLast, history.size)
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

    // --- side effects ---

    private suspend fun compressTurns(
        history: List<Turn>,
        splitAt: Int,
        lastSummary: Summary?,
    ): SummaryContent = when (lastSummary) {
        null -> compressor.compress(
            turns = history.subList(0, splitAt),
            previousSummary = null,
        )
        else -> compressor.compress(
            turns = history.subList(lastSummary.toTurnIndex.value, splitAt),
            previousSummary = lastSummary,
        )
    }

    private suspend fun saveSummary(
        sessionId: AgentSessionId,
        summaryContent: SummaryContent,
        toTurnIndex: Int,
    ) {
        summaryRepository.save(
            summary = Summary(
                sessionId = sessionId,
                content = summaryContent,
                fromTurnIndex = TurnIndex(value = 0),
                toTurnIndex = TurnIndex(value = toTurnIndex),
                createdAt = CreatedAt(value = Clock.System.now()),
            ),
        )
    }

    // --- pure functions ---

    private fun turnsToMessages(turns: List<Turn>): List<ContextMessage> =
        turns.flatMap {
            listOf(
                ContextMessage(role = MessageRole.User, content = it.userMessage),
                ContextMessage(role = MessageRole.Assistant, content = it.assistantMessage),
            )
        }

    private fun withoutCompression(history: List<Turn>, newMessage: MessageContent): PreparedContext =
        PreparedContext(
            messages = turnsToMessages(turns = history) + ContextMessage(role = MessageRole.User, content = newMessage),
            compressed = false,
            originalTurnCount = history.size,
            retainedTurnCount = history.size,
            summaryCount = 0,
        )

    private fun withExistingSummary(
        summary: Summary,
        history: List<Turn>,
        newMessage: MessageContent,
    ): PreparedContext {
        val retained = history.subList(summary.toTurnIndex.value, history.size)
        return PreparedContext(
            messages = summarizedMessages(
                summaryText = summary.content.value,
                retainedTurns = retained,
                newMessage = newMessage,
            ),
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = retained.size,
            summaryCount = 1,
        )
    }

    private fun withNewSummary(
        summaryContent: SummaryContent,
        history: List<Turn>,
        splitAt: Int,
        newMessage: MessageContent,
    ): PreparedContext {
        val retained = history.subList(splitAt, history.size)
        return PreparedContext(
            messages = summarizedMessages(summaryText = summaryContent.value, retainedTurns = retained, newMessage = newMessage),
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = retained.size,
            summaryCount = 1,
        )
    }

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

    private fun summarizedMessages(
        summaryText: String,
        retainedTurns: List<Turn>,
        newMessage: MessageContent,
    ): List<ContextMessage> =
        buildList {
            add(ContextMessage(role = MessageRole.System, content = MessageContent(value = "Previous conversation summary:\n$summaryText")))
            addAll(turnsToMessages(turns = retainedTurns))
            add(ContextMessage(role = MessageRole.User, content = newMessage))
        }
}
