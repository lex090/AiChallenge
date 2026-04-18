package com.ai.challenge.contextmanagement.memory.impl

import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.MemoryService
import com.ai.challenge.contextmanagement.memory.MemoryType
import com.ai.challenge.contextmanagement.strategy.FactExtractorPort
import com.ai.challenge.sharedkernel.event.DomainEvent
import com.ai.challenge.sharedkernel.event.DomainEventHandler
import com.ai.challenge.sharedkernel.port.SessionQueryPort

/**
 * Event Handler -- extracts user-level facts from a recorded turn
 * and persists them in user-scoped memory.
 *
 * Resolves the [UserId] owning the session via [SessionQueryPort].
 * Returns early (no-op) when the session has no associated user.
 * Replaces the full user fact set only when the extractor returns
 * non-empty results (avoids clearing facts on extraction errors).
 */
class UserFactExtractionHandler(
    private val memoryService: MemoryService,
    private val sessionQueryPort: SessionQueryPort,
    private val factExtractor: FactExtractorPort,
) : DomainEventHandler<DomainEvent.TurnRecorded> {

    override suspend fun handle(event: DomainEvent.TurnRecorded) {
        val userId = sessionQueryPort.getUserId(sessionId = event.sessionId) ?: return
        val scope = MemoryScope.User(userId = userId)
        val existingFacts = memoryService.provider(type = MemoryType.UserFacts).get(scope = scope)
        val extractedFacts = factExtractor.extractUserFacts(
            turnSnapshot = event.turnSnapshot,
            existingFacts = existingFacts,
        )
        if (extractedFacts.isNotEmpty()) {
            memoryService.provider(type = MemoryType.UserFacts).replace(
                scope = scope,
                facts = extractedFacts,
            )
        }
    }
}
