package com.ai.challenge.context

import com.ai.challenge.core.context.ContextCompressor
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

    @BeforeTest
    fun setup() {
        fakeCompressor = FakeContextCompressor()
        fakeSummaryRepo = InMemorySummaryRepository()
    }

    private fun createManager(
        maxTurns: Int = 5,
        retainLast: Int = 2,
        compressionInterval: Int = 3,
    ): DefaultContextManager =
        DefaultContextManager(
            strategy = TurnCountStrategy(
                maxTurns = maxTurns,
                retainLast = retainLast,
                compressionInterval = compressionInterval,
            ),
            compressor = fakeCompressor,
            summaryRepository = fakeSummaryRepo,
        )

    @Test
    fun `returns all turns when history is below threshold`() = runTest {
        val manager = createManager(maxTurns = 5, retainLast = 2)
        val history = turns(3)

        val result = manager.prepareContext(AgentSessionId("s1"), history, "new msg")

        assertFalse(result.compressed)
        assertEquals(3, result.originalTurnCount)
        assertEquals(3, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(7, result.messages.size)
        assertEquals(ContextMessage(MessageRole.User, "new msg"), result.messages.last())
    }

    @Test
    fun `first compression when reaching maxTurns`() = runTest {
        // maxTurns=5, retainLast=2 → first compression at 5 turns, splitAt=3
        val manager = createManager(maxTurns = 5, retainLast = 2, compressionInterval = 3)
        val history = turns(5)

        val result = manager.prepareContext(AgentSessionId("s1"), history, "new msg")

        assertTrue(result.compressed)
        assertEquals(5, result.originalTurnCount)
        assertEquals(2, result.retainedTurnCount)
        assertEquals(1, result.summaryCount)
        // 1 system (summary) + 2 retained * 2 + 1 new = 6
        assertEquals(6, result.messages.size)
        assertEquals(MessageRole.System, result.messages.first().role)
        assertTrue(result.messages.first().content.contains("Summary of 3 turns"))
        assertEquals(ContextMessage(MessageRole.User, "msg4"), result.messages[1])
        assertEquals(ContextMessage(MessageRole.Assistant, "resp4"), result.messages[2])
        assertEquals(ContextMessage(MessageRole.User, "msg5"), result.messages[3])
        assertEquals(ContextMessage(MessageRole.Assistant, "resp5"), result.messages[4])
        assertEquals(ContextMessage(MessageRole.User, "new msg"), result.messages[5])
        assertEquals(1, fakeCompressor.callCount)
        assertEquals(null, fakeCompressor.lastPreviousSummary)  // first compression — no previous summary
    }

    @Test
    fun `reuses existing summary without recompressing during interval`() = runTest {
        // maxTurns=5, retainLast=2, compressionInterval=3
        // First compression at 5 turns: summary covers [0,3), retain [3,5)
        // At 6,7 turns: reuse summary, no recompression (turnsSince=3,4 < 2+3=5)
        val manager = createManager(maxTurns = 5, retainLast = 2, compressionInterval = 3)
        val sessionId = AgentSessionId("s1")

        manager.prepareContext(sessionId, turns(5), "msg5")
        assertEquals(1, fakeCompressor.callCount)

        // 6 turns: turnsSinceCompression = 6-3 = 3, threshold = 2+3 = 5, 3 < 5 → no recompression
        val result6 = manager.prepareContext(sessionId, turns(6), "msg6")
        assertEquals(1, fakeCompressor.callCount) // NOT called again
        assertTrue(result6.compressed)
        // Uses existing summary + turns[3..6) = 3 turns as-is + new msg
        assertEquals(3, result6.retainedTurnCount)

        // 7 turns: turnsSinceCompression = 7-3 = 4, 4 < 5 → still no recompression
        val result7 = manager.prepareContext(sessionId, turns(7), "msg7")
        assertEquals(1, fakeCompressor.callCount) // still NOT called
        assertTrue(result7.compressed)
        assertEquals(4, result7.retainedTurnCount)
    }

    @Test
    fun `recompresses incrementally when interval exceeded`() = runTest {
        // maxTurns=5, retainLast=2, compressionInterval=3
        // First compression at 5 turns: summary covers [0,3)
        // At 8 turns: turnsSinceCompression = 8-3 = 5, threshold = 2+3 = 5, 5 >= 5 → recompress
        // New splitAt = 8-2 = 6, compress turns[3..6) with previousSummary
        val manager = createManager(maxTurns = 5, retainLast = 2, compressionInterval = 3)
        val sessionId = AgentSessionId("s1")

        manager.prepareContext(sessionId, turns(5), "msg5")
        assertEquals(1, fakeCompressor.callCount)

        val result = manager.prepareContext(sessionId, turns(8), "msg8")
        assertEquals(2, fakeCompressor.callCount)
        assertTrue(result.compressed)
        assertEquals(2, result.retainedTurnCount)
        // Incremental: previous summary was passed
        assertEquals("Summary of 3 turns", fakeCompressor.lastPreviousSummary?.text)
        // New turns compressed: turns[3..6) = 3 turns
        assertEquals(3, fakeCompressor.lastTurnCount)
        assertEquals(ContextMessage(MessageRole.User, "msg7"), result.messages[1])
        assertEquals(ContextMessage(MessageRole.User, "msg8"), result.messages[3])
    }

    @Test
    fun `handles empty history`() = runTest {
        val manager = createManager(maxTurns = 5, retainLast = 2)

        val result = manager.prepareContext(AgentSessionId("s1"), emptyList(), "hello")

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
