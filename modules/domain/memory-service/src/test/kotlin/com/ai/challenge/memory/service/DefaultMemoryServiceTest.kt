package com.ai.challenge.memory.service

import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.memory.FactMemoryProvider
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryType
import com.ai.challenge.core.memory.SummaryMemoryProvider
import com.ai.challenge.core.session.AgentSessionId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultMemoryServiceTest {

    @Test
    fun `provider returns typed FactMemoryProvider for Facts type`() {
        val service = createTestService()
        val provider: FactMemoryProvider = service.provider(type = MemoryType.Facts)
        assertTrue(actual = provider is InMemoryFactMemoryProvider)
    }

    @Test
    fun `provider returns typed SummaryMemoryProvider for Summaries type`() {
        val service = createTestService()
        val provider: SummaryMemoryProvider = service.provider(type = MemoryType.Summaries)
        assertTrue(actual = provider is InMemorySummaryMemoryProvider)
    }

    @Test
    fun `clearScope clears all providers`() = runTest {
        val factProvider = InMemoryFactMemoryProvider()
        val summaryProvider = InMemorySummaryMemoryProvider()
        val service = DefaultMemoryService(
            factMemoryProvider = factProvider,
            summaryMemoryProvider = summaryProvider,
        )
        val scope = MemoryScope.Session(sessionId = AgentSessionId(value = "s1"))

        factProvider.replace(scope = scope, facts = listOf(
            Fact(
                sessionId = AgentSessionId(value = "s1"),
                category = FactCategory.Goal,
                key = FactKey(value = "k"),
                value = FactValue(value = "v"),
            ),
        ))

        service.clearScope(scope = scope)

        assertTrue(actual = factProvider.get(scope = scope).isEmpty())
        assertTrue(actual = summaryProvider.get(scope = scope).isEmpty())
    }

    private fun createTestService(): DefaultMemoryService = DefaultMemoryService(
        factMemoryProvider = InMemoryFactMemoryProvider(),
        summaryMemoryProvider = InMemorySummaryMemoryProvider(),
    )
}
