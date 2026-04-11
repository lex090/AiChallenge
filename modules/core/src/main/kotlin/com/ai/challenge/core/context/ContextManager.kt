package com.ai.challenge.core.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.session.AgentSessionId

/**
 * Port — context preparation for Conversation Context.
 *
 * Called before each LLM message send to prepare the conversation
 * context according to the session's [ContextManagementType].
 *
 * Implemented in Context Management bounded context.
 *
 * Dependency direction: defined in core (Conversation Context),
 * implemented in context-manager module (Context Management Context).
 */
interface ContextManager {
    suspend fun prepareContext(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
    ): PreparedContext
}
