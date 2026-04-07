package com.ai.challenge.context

import com.ai.challenge.core.context.ContextManagementTypeRepository
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextManager.PreparedContext.ContextMessage
import com.ai.challenge.core.context.ContextManager.PreparedContext.ContextMessage.MessageRole
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.turn.TurnRepository
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
    private lateinit var fakeTurnRepo: InMemoryTurnRepository

    @BeforeTest
    fun setup() {
        fakeCompressor = FakeContextCompressor()
        fakeSummaryRepo = InMemorySummaryRepository()
        fakeContextManagementRepo = InMemoryContextManagementTypeRepository()
        fakeTurnRepo = InMemoryTurnRepository()
    }

    private fun createManager(): DefaultContextManager =
        DefaultContextManager(
            contextManagementRepository = fakeContextManagementRepo,
            compressor = fakeCompressor,
            summaryRepository = fakeSummaryRepo,
            turnRepository = fakeTurnRepo,
        )

    private suspend fun saveTurns(sessionId: AgentSessionId, turns: List<Turn>) {
        for (turn in turns) {
            fakeTurnRepo.append(sessionId, turn)
        }
    }

    @Test
    fun `returns all turns when type is None`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId, ContextManagementType.None)
        saveTurns(sessionId, turns(20))
        val manager = createManager()

        val result = manager.prepareContext(sessionId, "new msg")

        assertFalse(result.compressed)
        assertEquals(20, result.originalTurnCount)
        assertEquals(20, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
    }

    @Test
    fun `returns all turns when SummarizeOnThreshold and below threshold`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId, ContextManagementType.SummarizeOnThreshold)
        saveTurns(sessionId, turns(3))
        val manager = createManager()

        val result = manager.prepareContext(sessionId, "new msg")

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
        saveTurns(sessionId, turns(15))
        val manager = createManager()

        val result = manager.prepareContext(sessionId, "new msg")

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
        saveTurns(sessionId, turns(15))
        val manager = createManager()

        manager.prepareContext(sessionId, "msg15")
        assertEquals(1, fakeCompressor.callCount)

        fakeTurnRepo.append(sessionId, Turn(userMessage = "msg16", agentResponse = "resp16"))
        val result = manager.prepareContext(sessionId, "msg16")
        assertEquals(1, fakeCompressor.callCount)
        assertTrue(result.compressed)
    }

    @Test
    fun `handles empty history`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId, ContextManagementType.SummarizeOnThreshold)
        val manager = createManager()

        val result = manager.prepareContext(sessionId, "hello")

        assertFalse(result.compressed)
        assertEquals(0, result.originalTurnCount)
        assertEquals(1, result.messages.size)
        assertEquals(ContextMessage(MessageRole.User, "hello"), result.messages[0])
    }

    @Test
    fun `sliding window returns all turns when history is smaller than window`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId, ContextManagementType.SlidingWindow)
        saveTurns(sessionId, turns(5))
        val manager = createManager()

        val result = manager.prepareContext(sessionId, "new msg")

        assertFalse(result.compressed)
        assertEquals(5, result.originalTurnCount)
        assertEquals(5, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(11, result.messages.size)
        assertEquals(ContextMessage(MessageRole.User, "new msg"), result.messages.last())
    }

    @Test
    fun `sliding window retains only last 10 turns when history exceeds window`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId, ContextManagementType.SlidingWindow)
        saveTurns(sessionId, turns(15))
        val manager = createManager()

        val result = manager.prepareContext(sessionId, "new msg")

        assertFalse(result.compressed)
        assertEquals(15, result.originalTurnCount)
        assertEquals(10, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(21, result.messages.size)
        assertEquals(MessageRole.User, result.messages.first().role)
        assertEquals("msg6", result.messages.first().content)
        assertEquals(ContextMessage(MessageRole.User, "new msg"), result.messages.last())
    }

    @Test
    fun `sliding window handles empty history`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId, ContextManagementType.SlidingWindow)
        val manager = createManager()

        val result = manager.prepareContext(sessionId, "hello")

        assertFalse(result.compressed)
        assertEquals(0, result.originalTurnCount)
        assertEquals(0, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
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

private class InMemoryTurnRepository : TurnRepository {
    private val store = mutableListOf<Pair<AgentSessionId, Turn>>()

    override suspend fun append(sessionId: AgentSessionId, turn: Turn): TurnId {
        store.add(sessionId to turn)
        return turn.id
    }

    override suspend fun getBySession(sessionId: AgentSessionId, limit: Int?): List<Turn> =
        store.filter { it.first == sessionId }.map { it.second }

    override suspend fun get(turnId: TurnId): Turn? =
        store.map { it.second }.firstOrNull { it.id == turnId }
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
