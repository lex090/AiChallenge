package com.ai.challenge.context

import com.ai.challenge.core.context.CompressedContext
import com.ai.challenge.core.context.CompressionContext
import com.ai.challenge.core.context.CompressionDecision
import com.ai.challenge.core.context.ContextCompressor
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.ContextStrategy
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.turn.Turn

class DefaultContextManager(
    private val strategy: ContextStrategy,
    private val compressor: ContextCompressor,
    private val summaryRepository: SummaryRepository,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext {
        val existingSummaries = summaryRepository.getBySession(sessionId)
        val lastSummary = existingSummaries.maxByOrNull { it.toTurnIndex }
        val decision = strategy.evaluate(CompressionContext(history, lastSummary))

        return when (decision) {
            is CompressionDecision.Skip -> when (lastSummary) {
                null -> noCompression(history, newMessage)
                else -> reuseExistingSummary(lastSummary, history, newMessage)
            }

            is CompressionDecision.Compress ->
                compress(sessionId, history, newMessage, decision.partitionPoint, lastSummary)
        }
    }

    private suspend fun compress(
        sessionId: AgentSessionId,
        history: List<Turn>,
        newMessage: String,
        splitAt: Int,
        lastSummary: Summary?,
    ): CompressedContext {
        val toRetain = history.subList(splitAt, history.size)

        val summaryText = when (lastSummary) {
            null -> compressor.compress(history.subList(0, splitAt))
            else -> compressor.compress(
                history.subList(lastSummary.toTurnIndex, splitAt),
                previousSummary = lastSummary,
            )
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
