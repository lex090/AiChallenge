package com.ai.challenge.contextmanagement.usecase

import com.ai.challenge.contextmanagement.memory.MemorySnapshot
import com.ai.challenge.sharedkernel.identity.AgentSessionId

/**
 * Application Use Case -- get all agent memory for a session.
 *
 * Orchestration only, no business logic. Returns a [MemorySnapshot]
 * containing all memory types (facts, summaries) for the given session.
 */
interface GetMemoryUseCase {
    suspend fun execute(sessionId: AgentSessionId): MemorySnapshot
}
