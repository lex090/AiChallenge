package com.ai.challenge.core.memory.usecase

import com.ai.challenge.core.memory.MemorySnapshot
import com.ai.challenge.core.session.AgentSessionId

/**
 * Get all agent memory for a session.
 * Application Use Case: orchestration, no business logic.
 */
interface GetMemoryUseCase {
    suspend fun execute(sessionId: AgentSessionId): MemorySnapshot
}
