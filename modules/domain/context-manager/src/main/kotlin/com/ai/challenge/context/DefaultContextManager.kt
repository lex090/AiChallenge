package com.ai.challenge.context

import com.ai.challenge.core.CompressedContext
import com.ai.challenge.core.ContextCompressor
import com.ai.challenge.core.ContextManager
import com.ai.challenge.core.ContextMessage
import com.ai.challenge.core.CompressionStrategy
import com.ai.challenge.core.MessageRole
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Summary
import com.ai.challenge.core.SummaryRepository
import com.ai.challenge.core.Turn

class DefaultContextManager(
    private val strategy: CompressionStrategy,
    private val compressor: ContextCompressor,
    private val summaryRepository: SummaryRepository,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: SessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext {
        val existingSummaries = summaryRepository.getBySession(sessionId)
        val lastSummary = existingSummaries.maxByOrNull { it.toTurnIndex }

        if (!strategy.shouldCompress(history, lastSummary?.toTurnIndex)) {
            return if (lastSummary != null) {
                reuseExistingSummary(lastSummary, history, newMessage)
            } else {
                noCompression(history, newMessage)
            }
        }

        val splitAt = strategy.partitionPoint(history)
        val toRetain = history.subList(splitAt, history.size)

        val summaryText = if (lastSummary != null) {
            val newTurns = history.subList(lastSummary.toTurnIndex, splitAt)
            compressor.compress(newTurns, previousSummary = lastSummary)
        } else {
            val toCompress = history.subList(0, splitAt)
            compressor.compress(toCompress)
        }

        summaryRepository.save(
            sessionId,
            Summary(
                text = summaryText,
                fromTurnIndex = 0,
                toTurnIndex = splitAt,
            ),
        )

        return CompressedContext(
            messages = buildMessages(summaryText, toRetain, newMessage),
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = toRetain.size,
            summaryCount = 1,
        )
    }

    private fun noCompression(history: List<Turn>, newMessage: String): CompressedContext {
        val messages = buildList {
            for (turn in history) {
                add(ContextMessage(MessageRole.User, turn.userMessage))
                add(ContextMessage(MessageRole.Assistant, turn.agentResponse))
            }
            add(ContextMessage(MessageRole.User, newMessage))
        }
        return CompressedContext(
            messages = messages,
            compressed = false,
            originalTurnCount = history.size,
            retainedTurnCount = history.size,
            summaryCount = 0,
        )
    }

    private fun reuseExistingSummary(
        summary: Summary,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext {
        val turnsAfterSummary = history.subList(summary.toTurnIndex, history.size)
        return CompressedContext(
            messages = buildMessages(summary.text, turnsAfterSummary, newMessage),
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = turnsAfterSummary.size,
            summaryCount = 1,
        )
    }

    private fun buildMessages(
        summaryText: String,
        retainedTurns: List<Turn>,
        newMessage: String,
    ): List<ContextMessage> = buildList {
        add(ContextMessage(MessageRole.System, "Previous conversation summary:\n$summaryText"))
        for (turn in retainedTurns) {
            add(ContextMessage(MessageRole.User, turn.userMessage))
            add(ContextMessage(MessageRole.Assistant, turn.agentResponse))
        }
        add(ContextMessage(MessageRole.User, newMessage))
    }
}
