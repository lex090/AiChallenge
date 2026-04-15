package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.contextmanagement.model.Fact
import com.ai.challenge.contextmanagement.model.UserFact
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.vo.MessageContent
import com.ai.challenge.sharedkernel.vo.TurnSnapshot

/**
 * Port -- extracts structured facts from conversation messages.
 *
 * Used by [StickyFactsStrategy] to maintain a set of key facts
 * extracted from the conversation history.
 * Also used by [UserFactExtractionHandler] to extract user-level facts
 * from turn snapshots across sessions.
 *
 * Implemented in the data/infrastructure layer (e.g., via LLM).
 */
interface FactExtractorPort {
    suspend fun extract(
        sessionId: AgentSessionId,
        currentFacts: List<Fact>,
        newUserMessage: MessageContent,
        lastAssistantResponse: MessageContent?,
    ): List<Fact>

    suspend fun extractUserFacts(
        turnSnapshot: TurnSnapshot,
        existingFacts: List<UserFact>,
    ): List<UserFact>
}
