package com.ai.challenge.core.fact

import com.ai.challenge.core.session.AgentSessionId

/**
 * Repository — persistence for [Fact] value objects
 * in Context Management bounded context.
 *
 * Not part of [AgentSession] aggregate — internal state of
 * StickyFacts strategy. Accessed only by context management services.
 *
 * [save] implements replace-all semantics: deletes all session facts
 * and writes new ones. This is correct because [Fact] is a value object
 * without stable identity — facts are fully recreated on each message.
 */
interface FactRepository {
    suspend fun save(sessionId: AgentSessionId, facts: List<Fact>)
    suspend fun getBySession(sessionId: AgentSessionId): List<Fact>
    suspend fun deleteBySession(sessionId: AgentSessionId)
}
