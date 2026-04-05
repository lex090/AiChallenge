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

        if (!strategy.shouldCompress(history)) {
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

        val splitAt = strategy.partitionPoint(history)
        val toCompress = history.subList(0, splitAt)
        val toRetain = history.subList(splitAt, history.size)

        val existingSummaries = summaryRepository.getBySession(sessionId)
        val cached = existingSummaries.find {
            it.fromTurnIndex == 0 && it.toTurnIndex == splitAt
        }

        val summaryText = if (cached != null) {
            cached.text
        } else {
            val text = compressor.compress(toCompress)
            summaryRepository.save(
                sessionId,
                Summary(
                    text = text,
                    fromTurnIndex = 0,
                    toTurnIndex = splitAt,
                ),
            )
            text
        }

        val messages = buildList {
            add(ContextMessage(MessageRole.System, "Previous conversation summary:\n$summaryText"))
            for (turn in toRetain) {
                add(ContextMessage(MessageRole.User, turn.userMessage))
                add(ContextMessage(MessageRole.Assistant, turn.agentResponse))
            }
            add(ContextMessage(MessageRole.User, newMessage))
        }

        return CompressedContext(
            messages = messages,
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = toRetain.size,
            summaryCount = 1,
        )
    }
}
