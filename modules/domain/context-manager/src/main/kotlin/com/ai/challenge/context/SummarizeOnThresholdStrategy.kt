package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.ContextStrategyConfig
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.context.PreparedContext
import com.ai.challenge.core.context.model.SummaryContent
import com.ai.challenge.core.context.model.TurnIndex
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.turn.Turn
import kotlin.time.Clock

class SummarizeOnThresholdStrategy(
    private val repository: AgentSessionRepository,
    private val compressor: ContextCompressor,
    private val summaryRepository: SummaryRepository,
) : ContextStrategy {

    override suspend fun prepare(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        config: ContextStrategyConfig,
    ): PreparedContext {
        val summarizeConfig = config as ContextStrategyConfig.SummarizeOnThreshold
        val history = repository.getTurnsByBranch(branchId = branchId)

        if (history.size < summarizeConfig.maxTurnsBeforeCompression) {
            return withoutCompression(history = history, newMessage = newMessage)
        }

        val lastSummary = summaryRepository.getBySession(sessionId = sessionId).maxByOrNull { it.toTurnIndex.value }

        if (lastSummary != null) {
            val turnsSinceLastSummary = history.size - lastSummary.toTurnIndex.value
            if (turnsSinceLastSummary < summarizeConfig.retainLastTurns + summarizeConfig.compressionInterval) {
                return withExistingSummary(summary = lastSummary, history = history, newMessage = newMessage)
            }
        }

        val splitAt = (history.size - summarizeConfig.retainLastTurns).coerceAtLeast(minimumValue = 0)
        val summaryContent = compressTurns(history = history, splitAt = splitAt, lastSummary = lastSummary)
        saveSummary(sessionId = sessionId, summaryContent = summaryContent, toTurnIndex = splitAt)
        return withNewSummary(summaryContent = summaryContent, history = history, splitAt = splitAt, newMessage = newMessage)
    }

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
