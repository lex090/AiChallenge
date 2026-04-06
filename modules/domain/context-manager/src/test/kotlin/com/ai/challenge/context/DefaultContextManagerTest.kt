package com.ai.challenge.context

import com.ai.challenge.core.context.ContextCompressor
import com.ai.challenge.core.context.ContextManagementTypeRepository
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.turn.Turn
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultContextManagerTest {

    private fun turns(count: Int): List<Turn> =
        (1..count).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") }

    private lateinit var fakeCompressor: FakeContextCompressor
    private lateinit var fakeSummaryRepo: InMemorySummaryRepository
    private lateinit var fakeContextManagementRepo: InMemoryContextManagementTypeRepository

    @BeforeTest
    fun setup() {
        fakeCompressor = FakeContextCompressor()
        fakeSummaryRepo = InMemorySummaryRepository()
        fakeContextManagementRepo = InMemoryContextManagementTypeRepository()
    }

    private fun createManager(): DefaultContextManager =
        DefaultContextManager(
            contextManagementRepository = fakeContextManagementRepo,
            strategyFactory = ContextStrategyFactory(),
            compressor = fakeCompressor,
            summaryRepository = fakeSummaryRepo,
        )

    @Test
    fun `returns all turns when type is None`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId, ContextManagementType.None)
        val manager = createManager()
        val history = turns(20)

        val result = manager.prepareContext(sessionId, history, "new msg")

        assertFalse(result.compressed)
        assertEquals(20, result.originalTurnCount)
        assertEquals(20, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
    }

    @Test
    fun `returns all turns when SummarizeOnThreshold and below threshold`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId, ContextManagementType.SummarizeOnThreshold)
        val manager = createManager()
        val history = turns(3)

        val result = manager.prepareContext(sessionId, history, "new msg")

        assertFalse(result.compressed)
        assertEquals(3, result.originalTurnCount)
        assertEquals(3, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(7, result.messages.size)
        assertEquals(ContextMessage(MessageRole.User, "new msg"), result.messages.last())
    }

    @Test
    fun `compresses when SummarizeOnThreshold and at threshold`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId, ContextManagementType.SummarizeOnThreshold)
        val manager = createManager()
        val history = turns(15)

        val result = manager.prepareContext(sessionId, history, "new msg")

        assertTrue(result.compressed)
        assertEquals(15, result.originalTurnCount)
        assertEquals(5, result.retainedTurnCount)
        assertEquals(1, result.summaryCount)
        assertEquals(MessageRole.System, result.messages.first().role)
        assertEquals(1, fakeCompressor.callCount)
    }

    @Test
    fun `reuses existing summary without recompressing during interval`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId, ContextManagementType.SummarizeOnThreshold)
        val manager = createManager()

        manager.prepareContext(sessionId, turns(15), "msg15")
        assertEquals(1, fakeCompressor.callCount)

        val result = manager.prepareContext(sessionId, turns(16), "msg16")
        assertEquals(1, fakeCompressor.callCount)
        assertTrue(result.compressed)
    }

    @Test
    fun `handles empty history`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId, ContextManagementType.SummarizeOnThreshold)
        val manager = createManager()

        val result = manager.prepareContext(sessionId, emptyList(), "hello")

        assertFalse(result.compressed)
        assertEquals(0, result.originalTurnCount)
        assertEquals(1, result.messages.size)
        assertEquals(ContextMessage(MessageRole.User, "hello"), result.messages[0])
    }
}

private class FakeContextCompressor : ContextCompressor {
    var callCount = 0
        private set
    var lastPreviousSummary: Summary? = null
        private set
    var lastTurnCount = 0
        private set

    override suspend fun compress(turns: List<Turn>, previousSummary: Summary?): String {
        callCount++
        lastPreviousSummary = previousSummary
        lastTurnCount = turns.size
        return "Summary of ${turns.size} turns"
    }
}

private class InMemorySummaryRepository : SummaryRepository {
    private val store = mutableListOf<Pair<AgentSessionId, Summary>>()

    override suspend fun save(sessionId: AgentSessionId, summary: Summary) {
        store.add(sessionId to summary)
    }

    override suspend fun getBySession(sessionId: AgentSessionId): List<Summary> =
        store.filter { it.first == sessionId }.map { it.second }
}

private class InMemoryContextManagementTypeRepository : ContextManagementTypeRepository {
    private val store = mutableMapOf<AgentSessionId, ContextManagementType>()

    override suspend fun save(sessionId: AgentSessionId, type: ContextManagementType) {
        store[sessionId] = type
    }

    override suspend fun getBySession(sessionId: AgentSessionId): ContextManagementType =
        store[sessionId] ?: ContextManagementType.None

    override suspend fun delete(sessionId: AgentSessionId) {
        store.remove(sessionId)
    }
}
