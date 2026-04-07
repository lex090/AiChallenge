package com.ai.challenge.context

import com.ai.challenge.core.context.CompressedContext
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextManagementTypeRepository
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
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
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        newMessage: String,
    ): CompressedContext {
        val type = contextManagementRepository.getBySession(sessionId = sessionId)

        return when (type) {
            is ContextManagementType.None -> passThrough(sessionId = sessionId, newMessage = newMessage)
            is ContextManagementType.SummarizeOnThreshold -> summarizeOnThreshold(
                sessionId = sessionId,
                newMessage = newMessage
            )
        }
    }

    // --- orchestration (side effects at boundaries) ---

    private suspend fun passThrough(
        sessionId: AgentSessionId,
        newMessage: String,
    ): CompressedContext {
        val history = turnRepository.getBySession(sessionId = sessionId)
        return withoutCompression(history = history, newMessage = newMessage)
    }

    private suspend fun summarizeOnThreshold(
        sessionId: AgentSessionId,
        newMessage: String,
    ): CompressedContext {
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

    private fun withoutCompression(history: List<Turn>, newMessage: String): CompressedContext =
        CompressedContext(
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
    ): CompressedContext {
        val retained = history.subList(summary.toTurnIndex, history.size)
        return CompressedContext(
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
    ): CompressedContext {
        val retained = history.subList(splitAt, history.size)
        return CompressedContext(
            messages = summarizedMessages(summaryText = summaryText, retainedTurns = retained, newMessage = newMessage),
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = retained.size,
            summaryCount = 1,
        )
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
