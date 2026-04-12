package com.ai.challenge.memory.service.handler

import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.event.DomainEvent
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.memory.service.DefaultMemoryService
import com.ai.challenge.memory.service.InMemoryFactMemoryProvider
import com.ai.challenge.memory.service.InMemorySummaryMemoryProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SessionDeletedCleanupHandlerTest {

    @Test
    fun `handle clears all memory for deleted session`() = runTest {
        val factProvider = InMemoryFactMemoryProvider()
        val summaryProvider = InMemorySummaryMemoryProvider()
        val memoryService = DefaultMemoryService(
            factMemoryProvider = factProvider,
            summaryMemoryProvider = summaryProvider,
        )
        val sessionId = AgentSessionId.generate()
        val scope = MemoryScope.Session(sessionId = sessionId)

        factProvider.replace(scope = scope, facts = listOf(
            Fact(
                sessionId = sessionId,
                category = FactCategory.Goal,
                key = FactKey(value = "k"),
                value = FactValue(value = "v"),
            ),
        ))

        val handler = SessionDeletedCleanupHandler(memoryService = memoryService)
        handler.handle(event = DomainEvent.SessionDeleted(sessionId = sessionId))

        assertTrue(actual = factProvider.get(scope = scope).isEmpty())
        assertTrue(actual = summaryProvider.get(scope = scope).isEmpty())
    }

    @Test
    fun `handle with no data does not throw`() = runTest {
        val memoryService = DefaultMemoryService(
            factMemoryProvider = InMemoryFactMemoryProvider(),
            summaryMemoryProvider = InMemorySummaryMemoryProvider(),
        )
        val handler = SessionDeletedCleanupHandler(memoryService = memoryService)
        handler.handle(event = DomainEvent.SessionDeleted(sessionId = AgentSessionId.generate()))
    }
}
