package com.ai.challenge.sharedkernel.port

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.vo.ContextModeId
import com.ai.challenge.sharedkernel.vo.MessageContent
import com.ai.challenge.sharedkernel.vo.PreparedContext
import com.ai.challenge.sharedkernel.vo.SystemInstructions

/**
 * Port -- context preparation for Conversation bounded context.
 *
 * Called before each LLM message send to prepare the conversation
 * context according to the session's [ContextModeId].
 *
 * Implemented in Context Management bounded context.
 *
 * Dependency direction: defined in shared-kernel,
 * implemented in context-manager module (Context Management Context),
 * consumed by conversation/domain (Conversation Context).
 */
interface ContextManagerPort {
    suspend fun prepareContext(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        contextModeId: ContextModeId,
        projectInstructions: SystemInstructions?,
    ): PreparedContext
}
