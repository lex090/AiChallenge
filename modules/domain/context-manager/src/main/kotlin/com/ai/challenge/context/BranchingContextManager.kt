package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchRepository
import com.ai.challenge.core.branch.BranchTurnRepository
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.ContextManager.PreparedContext
import com.ai.challenge.core.context.ContextManager.PreparedContext.ContextMessage
import com.ai.challenge.core.context.ContextManager.PreparedContext.ContextMessage.MessageRole
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnRepository

class BranchingContextManager(
    private val turnRepository: TurnRepository,
    private val branchRepository: BranchRepository,
    private val branchTurnRepository: BranchTurnRepository,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        newMessage: String,
    ): PreparedContext {
        val activeBranch = branchRepository.getActiveBranch(sessionId = sessionId)
            ?: error("No active branch for session ${sessionId.value}")
        val turnIds = branchTurnRepository.getTurnIds(branchId = activeBranch.id)
        val turns = turnIds.mapNotNull { turnRepository.get(turnId = it) }
        val messages = turnsToMessages(turns = turns) +
            ContextMessage(role = MessageRole.User, content = newMessage)
        return PreparedContext(
            messages = messages,
            compressed = false,
            originalTurnCount = turns.size,
            retainedTurnCount = turns.size,
            summaryCount = 0,
        )
    }

    private fun turnsToMessages(turns: List<Turn>): List<ContextMessage> =
        turns.flatMap {
            listOf(
                ContextMessage(role = MessageRole.User, content = it.userMessage),
                ContextMessage(role = MessageRole.Assistant, content = it.agentResponse),
            )
        }
}
