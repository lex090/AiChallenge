package com.ai.challenge.context

import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.context.model.SummaryContent
import com.ai.challenge.core.context.model.TurnIndex
import com.ai.challenge.core.event.DomainEvent
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.summary.Summary
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock

class SessionDeletedCleanupHandlerTest {

    @Test
    fun `handle deletes facts and summaries for session`() = runTest {
        val sessionId = AgentSessionId.generate()
        val factRepo = InMemoryFactRepository()
        val summaryRepo = InMemorySummaryRepository()

        factRepo.save(
            sessionId = sessionId,
            facts = listOf(
                Fact(
                    sessionId = sessionId,
                    category = FactCategory.Goal,
                    key = FactKey(value = "k"),
                    value = FactValue(value = "v"),
                ),
            ),
        )
        summaryRepo.save(
            summary = Summary(
                sessionId = sessionId,
                content = SummaryContent(value = "test summary"),
                fromTurnIndex = TurnIndex(value = 0),
                toTurnIndex = TurnIndex(value = 1),
                createdAt = CreatedAt(value = Clock.System.now()),
            ),
        )

        val handler = SessionDeletedCleanupHandler(
            factRepository = factRepo,
            summaryRepository = summaryRepo,
        )
        handler.handle(event = DomainEvent.SessionDeleted(sessionId = sessionId))

        assertTrue(actual = factRepo.getBySession(sessionId = sessionId).isEmpty())
        assertTrue(actual = summaryRepo.getBySession(sessionId = sessionId).isEmpty())
    }

    @Test
    fun `handle with no data does not throw`() = runTest {
        val sessionId = AgentSessionId.generate()
        val factRepo = InMemoryFactRepository()
        val summaryRepo = InMemorySummaryRepository()

        val handler = SessionDeletedCleanupHandler(
            factRepository = factRepo,
            summaryRepository = summaryRepo,
        )
        handler.handle(event = DomainEvent.SessionDeleted(sessionId = sessionId))
    }
}
