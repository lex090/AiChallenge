package com.ai.challenge.context

import com.ai.challenge.core.CompressedContext
import com.ai.challenge.core.ContextManager
import com.ai.challenge.core.ContextMessage
import com.ai.challenge.core.FactExtractor
import com.ai.challenge.core.FactRepository
import com.ai.challenge.core.MessageRole
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn

class StickyFactsContextManager(
    private val factExtractor: FactExtractor,
    private val factRepository: FactRepository,
    private val windowSize: Int,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: SessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext {
        val currentFacts = factRepository.getBySession(sessionId)
        val updatedFacts = factExtractor.extract(history, currentFacts, newMessage)
        factRepository.save(sessionId, updatedFacts)

        val retained = if (history.size > windowSize) {
            history.takeLast(windowSize)
        } else {
            history
        }

        val factsText = if (updatedFacts.isNotEmpty()) {
            updatedFacts.joinToString("\n") { "- ${it.key}: ${it.value}" }
        } else {
            null
        }

        val messages = buildList {
            if (factsText != null) {
                add(
                    ContextMessage(
                        MessageRole.System,
                        "Known facts about this conversation:\n$factsText",
                    ),
                )
            }
            for (turn in retained) {
                add(ContextMessage(MessageRole.User, turn.userMessage))
                add(ContextMessage(MessageRole.Assistant, turn.agentResponse))
            }
            add(ContextMessage(MessageRole.User, newMessage))
        }

        return CompressedContext(
            messages = messages,
            compressed = updatedFacts.isNotEmpty(),
            originalTurnCount = history.size,
            retainedTurnCount = retained.size,
            summaryCount = 0,
        )
    }
}
