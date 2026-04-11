package com.ai.challenge.context

import com.ai.challenge.core.event.DomainEvent
import com.ai.challenge.core.event.DomainEventHandler
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.summary.SummaryRepository

/**
 * Event Handler — cleans up Context Management data
 * when a session is deleted in Conversation Context.
 *
 * Resolves orphaning: without this handler, facts and summaries
 * persist in the database after session deletion.
 *
 * Listens to: [DomainEvent.SessionDeleted]
 * Actions: deletes all Facts and Summaries for the session.
 */
class SessionDeletedCleanupHandler(
    private val factRepository: FactRepository,
    private val summaryRepository: SummaryRepository,
) : DomainEventHandler<DomainEvent.SessionDeleted> {

    override suspend fun handle(event: DomainEvent.SessionDeleted) {
        factRepository.deleteBySession(sessionId = event.sessionId)
        summaryRepository.deleteBySession(sessionId = event.sessionId)
    }
}
