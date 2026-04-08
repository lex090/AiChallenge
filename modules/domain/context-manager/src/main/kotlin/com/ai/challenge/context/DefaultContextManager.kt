package com.ai.challenge.context

import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextManagementTypeRepository
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.ContextManager.PreparedContext
import com.ai.challenge.core.context.ContextManager.PreparedContext.ContextMessage
import com.ai.challenge.core.context.ContextManager.PreparedContext.ContextMessage.MessageRole
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnRepository

class DefaultContextManager(
    private val contextManagementRepository: ContextManagementTypeRepository,
    private val compressor: ContextCompressor,
    private val summaryRepository: SummaryRepository,
    private val turnRepository: TurnRepository,
    private val factExtractor: FactExtractor,
    private val factRepository: FactRepository,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        newMessage: String,
    ): PreparedContext {
        val type = contextManagementRepository.getBySession(sessionId = sessionId)

        return when (type) {
            is ContextManagementType.None -> passThrough(sessionId = sessionId, newMessage = newMessage)
            is ContextManagementType.SummarizeOnThreshold -> summarizeOnThreshold(
                sessionId = sessionId,
                newMessage = newMessage
            )
            is ContextManagementType.StickyFacts -> stickyFacts(sessionId = sessionId, newMessage = newMessage)
        }
    }

    // --- orchestration (side effects at boundaries) ---

    private suspend fun passThrough(
        sessionId: AgentSessionId,
        newMessage: String,
    ): PreparedContext {
        val history = turnRepository.getBySession(sessionId = sessionId)
        return withoutCompression(history = history, newMessage = newMessage)
    }

    private suspend fun summarizeOnThreshold(
        sessionId: AgentSessionId,
        newMessage: String,
    ): PreparedContext {
        val maxTurns = 15
        val retainLast = 5
        val compressionInterval = 10

        val history = turnRepository.getBySession(sessionId = sessionId)

        if (history.size < maxTurns) {
            return withoutCompression(history = history, newMessage = newMessage)
        }

        val lastSummary = summaryRepository.getBySession(sessionId = sessionId).maxByOrNull { it.toTurnIndex }

        if (lastSummary != null) {
            val turnsSinceLastSummary = history.size - lastSummary.toTurnIndex
            if (turnsSinceLastSummary < retainLast + compressionInterval) {
                return withExistingSummary(summary = lastSummary, history = history, newMessage = newMessage)
            }
        }

        val splitAt = (history.size - retainLast).coerceAtLeast(minimumValue = 0)
        val summaryText = compressTurns(history = history, splitAt = splitAt, lastSummary = lastSummary)
        saveSummary(sessionId = sessionId, summaryText = summaryText, toTurnIndex = splitAt)
        return withNewSummary(summaryText = summaryText, history = history, splitAt = splitAt, newMessage = newMessage)
    }

    private suspend fun stickyFacts(
        sessionId: AgentSessionId,
        newMessage: String,
    ): PreparedContext {
        val retainLast = 5

        val currentFacts = factRepository.getBySession(sessionId = sessionId)
        val history = turnRepository.getBySession(sessionId = sessionId)
        val lastAssistantResponse = history.lastOrNull()?.agentResponse

        val updatedFacts = factExtractor.extract(
            currentFacts = currentFacts,
            newUserMessage = newMessage,
            lastAssistantResponse = lastAssistantResponse,
        )
        factRepository.save(sessionId = sessionId, facts = updatedFacts)

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
    ): String = when (lastSummary) {
        null -> compressor.compress(turns = history.subList(0, splitAt))
        else -> compressor.compress(
            turns = history.subList(lastSummary.toTurnIndex, splitAt),
            previousSummary = lastSummary,
        )
    }

    private suspend fun saveSummary(
        sessionId: AgentSessionId,
        summaryText: String,
        toTurnIndex: Int,
    ) {
        summaryRepository.save(
            sessionId = sessionId,
            summary = Summary(text = summaryText, fromTurnIndex = 0, toTurnIndex = toTurnIndex),
        )
    }

    // --- pure functions ---

    private fun turnsToMessages(turns: List<Turn>): List<ContextMessage> =
        turns.flatMap {
            listOf(
                ContextMessage(role = MessageRole.User, content = it.userMessage),
                ContextMessage(role = MessageRole.Assistant, content = it.agentResponse),
            )
        }

    private fun withoutCompression(history: List<Turn>, newMessage: String): PreparedContext =
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
        newMessage: String,
    ): PreparedContext {
        val retained = history.subList(summary.toTurnIndex, history.size)
        return PreparedContext(
            messages = summarizedMessages(
                summaryText = summary.text,
                retainedTurns = retained,
                newMessage = newMessage
            ),
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = retained.size,
            summaryCount = 1,
        )
    }

    private fun withNewSummary(
        summaryText: String,
        history: List<Turn>,
        splitAt: Int,
        newMessage: String,
    ): PreparedContext {
        val retained = history.subList(splitAt, history.size)
        return PreparedContext(
            messages = summarizedMessages(summaryText = summaryText, retainedTurns = retained, newMessage = newMessage),
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
        newMessage: String,
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
        newMessage: String,
    ): List<ContextMessage> =
        buildList {
            add(ContextMessage(role = MessageRole.System, content = formatFacts(facts = facts)))
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
            appendLine("- ${fact.key}: ${fact.value}")
        }
        appendLine()
    }

    private fun summarizedMessages(
        summaryText: String,
        retainedTurns: List<Turn>,
        newMessage: String,
    ): List<ContextMessage> =
        buildList {
            add(ContextMessage(role = MessageRole.System, content = "Previous conversation summary:\n$summaryText"))
            addAll(turnsToMessages(turns = retainedTurns))
            add(ContextMessage(role = MessageRole.User, content = newMessage))
        }
}
