package com.ai.challenge.sharedkernel.port

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.vo.TurnSnapshot

/**
 * Port -- read-only access to Turn data for cross-context queries.
 *
 * Defined in shared kernel so that Context Management bounded context
 * can query conversation turns without depending on Conversation's
 * aggregate internals. Returns [TurnSnapshot] projections instead
 * of full Turn entities.
 *
 * Implemented by Conversation bounded context (or its data layer).
 *
 * Dependency direction: defined in shared-kernel,
 * implemented in conversation/data, consumed by context-management/domain.
 */
interface TurnQueryPort {
    suspend fun findBySessionAndBranch(
        sessionId: AgentSessionId,
        branchId: BranchId,
    ): List<TurnSnapshot>
}
