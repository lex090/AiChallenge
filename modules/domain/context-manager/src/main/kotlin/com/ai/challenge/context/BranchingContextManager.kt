package com.ai.challenge.context

import com.ai.challenge.core.BranchRepository
import com.ai.challenge.core.CompressedContext
import com.ai.challenge.core.ContextManager
import com.ai.challenge.core.ContextMessage
import com.ai.challenge.core.MessageRole
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn

class BranchingContextManager(
    private val branchRepository: BranchRepository,
    private val windowSize: Int,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: SessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext {
        val activeBranch = branchRepository.getActiveBranch(sessionId)

        val combinedHistory = if (activeBranch != null) {
            val mainHistory = history.take(activeBranch.checkpointTurnIndex)
            val branchTurns = branchRepository.getTurnsForBranch(activeBranch.id)
            mainHistory + branchTurns
        } else {
            history
        }

        val retained = if (combinedHistory.size > windowSize) {
            combinedHistory.takeLast(windowSize)
        } else {
            combinedHistory
        }

        val messages = buildList {
            for (turn in retained) {
                add(ContextMessage(MessageRole.User, turn.userMessage))
                add(ContextMessage(MessageRole.Assistant, turn.agentResponse))
            }
            add(ContextMessage(MessageRole.User, newMessage))
        }

        return CompressedContext(
            messages = messages,
            compressed = false,
            originalTurnCount = combinedHistory.size,
            retainedTurnCount = retained.size,
            summaryCount = 0,
        )
    }
}
