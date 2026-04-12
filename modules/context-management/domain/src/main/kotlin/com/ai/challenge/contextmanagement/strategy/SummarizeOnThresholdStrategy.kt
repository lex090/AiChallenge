package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.MemoryService
import com.ai.challenge.contextmanagement.memory.MemoryType
import com.ai.challenge.contextmanagement.model.ContextStrategyConfig
import com.ai.challenge.contextmanagement.model.Summary
import com.ai.challenge.contextmanagement.model.SummaryContent
import com.ai.challenge.contextmanagement.model.TurnIndex
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.port.TurnQueryPort
import com.ai.challenge.sharedkernel.vo.ContextMessage
import com.ai.challenge.sharedkernel.vo.CreatedAt
import com.ai.challenge.sharedkernel.vo.MessageContent
import com.ai.challenge.sharedkernel.vo.MessageRole
import com.ai.challenge.sharedkernel.vo.PreparedContext
import com.ai.challenge.sharedkernel.vo.TurnSnapshot
import kotlin.time.Clock

/**
 * Strategy -- compress old turns when history exceeds threshold.
 *
 * When the number of turns exceeds [ContextStrategyConfig.SummarizeOnThreshold.maxTurnsBeforeCompression],
 * older turns are compressed into a summary via [ContextCompressorPort].
 * Recent turns are retained alongside the summary.
 * Used with [ContextManagementType.SummarizeOnThreshold].
 */
class SummarizeOnThresholdStrategy(
    private val turnQueryPort: TurnQueryPort,
    private val compressor: ContextCompressorPort,
    private val memoryService: MemoryService,
) : ContextStrategy {

    override suspend fun prepare(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        config: ContextStrategyConfig,
    ): PreparedContext {
        val summarizeConfig = config as ContextStrategyConfig.SummarizeOnThreshold
        val history = turnQueryPort.getTurnSnapshots(sessionId = sessionId, branchId = branchId)

        if (history.size < summarizeConfig.maxTurnsBeforeCompression) {
            return withoutCompression(history = history, newMessage = newMessage)
        }

        val summaryProvider = memoryService.provider(type = MemoryType.Summaries)
        val scope = MemoryScope.Session(sessionId = sessionId)
        val lastSummary = summaryProvider.get(scope = scope).maxByOrNull { it.toTurnIndex.value }

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
        history: List<TurnSnapshot>,
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
        val summaryProvider = memoryService.provider(type = MemoryType.Summaries)
        val scope = MemoryScope.Session(sessionId = sessionId)
        summaryProvider.append(
            scope = scope,
            summary = Summary(
                sessionId = sessionId,
                content = summaryContent,
                fromTurnIndex = TurnIndex(value = 0),
                toTurnIndex = TurnIndex(value = toTurnIndex),
                createdAt = CreatedAt(value = Clock.System.now()),
            ),
        )
    }

    private fun withoutCompression(history: List<TurnSnapshot>, newMessage: MessageContent): PreparedContext =
        PreparedContext(
            messages = snapshotsToMessages(snapshots = history) + ContextMessage(role = MessageRole.User, content = newMessage),
            compressed = false,
            originalTurnCount = history.size,
            retainedTurnCount = history.size,
            summaryCount = 0,
        )

    private fun withExistingSummary(
        summary: Summary,
        history: List<TurnSnapshot>,
        newMessage: MessageContent,
    ): PreparedContext {
        val retained = history.subList(summary.toTurnIndex.value, history.size)
        return PreparedContext(
            messages = summarizedMessages(
                summaryText = summary.content.value,
                retainedSnapshots = retained,
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
        history: List<TurnSnapshot>,
        splitAt: Int,
        newMessage: MessageContent,
    ): PreparedContext {
        val retained = history.subList(splitAt, history.size)
        return PreparedContext(
            messages = summarizedMessages(summaryText = summaryContent.value, retainedSnapshots = retained, newMessage = newMessage),
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = retained.size,
            summaryCount = 1,
        )
    }

    private fun summarizedMessages(
        summaryText: String,
        retainedSnapshots: List<TurnSnapshot>,
        newMessage: MessageContent,
    ): List<ContextMessage> =
        buildList {
            add(ContextMessage(role = MessageRole.System, content = MessageContent(value = "Previous conversation summary:\n$summaryText")))
            addAll(snapshotsToMessages(snapshots = retainedSnapshots))
            add(ContextMessage(role = MessageRole.User, content = newMessage))
        }
}
