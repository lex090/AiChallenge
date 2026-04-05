package com.ai.challenge.context

import com.ai.challenge.core.ContextCompressor
import com.ai.challenge.core.ContextMessage
import com.ai.challenge.core.MessageRole
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Summary
import com.ai.challenge.core.SummaryRepository
import com.ai.challenge.core.Turn
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultContextManagerTest {

    private fun turns(count: Int): List<Turn> =
        (1..count).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") }

    private val fakeCompressor = FakeContextCompressor()
    private val fakeSummaryRepo = InMemorySummaryRepository()

    private fun createManager(maxTurns: Int = 3, retainLast: Int = 1): DefaultContextManager =
        DefaultContextManager(
            strategy = TurnCountStrategy(maxTurns = maxTurns, retainLast = retainLast),
            compressor = fakeCompressor,
            summaryRepository = fakeSummaryRepo,
        )

    @Test
    fun `returns all turns when history is below threshold`() = runTest {
        val manager = createManager(maxTurns = 5, retainLast = 2)
        val history = turns(3)

        val result = manager.prepareContext(SessionId("s1"), history, "new msg")

        assertFalse(result.compressed)
        assertEquals(3, result.originalTurnCount)
        assertEquals(3, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(7, result.messages.size) // 3 turns * 2 messages + 1 new message
        assertEquals(ContextMessage(MessageRole.User, "new msg"), result.messages.last())
    }

    @Test
    fun `compresses old turns and retains recent ones`() = runTest {
        val manager = createManager(maxTurns = 3, retainLast = 2)
        val history = turns(5)

        val result = manager.prepareContext(SessionId("s1"), history, "new msg")

        assertTrue(result.compressed)
        assertEquals(5, result.originalTurnCount)
        assertEquals(2, result.retainedTurnCount)
        assertEquals(1, result.summaryCount)
        // 1 system (summary) + 2 retained turns * 2 + 1 new message = 6
        assertEquals(6, result.messages.size)
        assertEquals(MessageRole.System, result.messages.first().role)
        assertTrue(result.messages.first().content.contains("Summary of 3 turns"))
        assertEquals(ContextMessage(MessageRole.User, "msg4"), result.messages[1])
        assertEquals(ContextMessage(MessageRole.Assistant, "resp4"), result.messages[2])
        assertEquals(ContextMessage(MessageRole.User, "msg5"), result.messages[3])
        assertEquals(ContextMessage(MessageRole.Assistant, "resp5"), result.messages[4])
        assertEquals(ContextMessage(MessageRole.User, "new msg"), result.messages[5])
    }

    @Test
    fun `uses cached summary when range matches`() = runTest {
        val manager = createManager(maxTurns = 3, retainLast = 2)
        val history = turns(5)
        val sessionId = SessionId("s1")

        manager.prepareContext(sessionId, history, "first call")
        assertEquals(1, fakeCompressor.callCount)

        manager.prepareContext(sessionId, history, "second call")
        assertEquals(1, fakeCompressor.callCount) // not called again
    }

    @Test
    fun `creates new summary when range shifts`() = runTest {
        val manager = createManager(maxTurns = 3, retainLast = 2)
        val sessionId = SessionId("s1")

        manager.prepareContext(sessionId, turns(5), "call1")
        assertEquals(1, fakeCompressor.callCount)

        // History grew — splitAt shifts from 3 to 5
        manager.prepareContext(sessionId, turns(7), "call2")
        assertEquals(2, fakeCompressor.callCount)
    }

    @Test
    fun `handles empty history`() = runTest {
        val manager = createManager(maxTurns = 3, retainLast = 2)

        val result = manager.prepareContext(SessionId("s1"), emptyList(), "hello")

        assertFalse(result.compressed)
        assertEquals(0, result.originalTurnCount)
        assertEquals(1, result.messages.size)
        assertEquals(ContextMessage(MessageRole.User, "hello"), result.messages[0])
    }
}

private class FakeContextCompressor : ContextCompressor {
    var callCount = 0
        private set

    override suspend fun compress(turns: List<Turn>): String {
        callCount++
        return "Summary of ${turns.size} turns"
    }
}

private class InMemorySummaryRepository : SummaryRepository {
    private val store = mutableListOf<Pair<SessionId, Summary>>()

    override suspend fun save(sessionId: SessionId, summary: Summary) {
        store.add(sessionId to summary)
    }

    override suspend fun getBySession(sessionId: SessionId): List<Summary> =
        store.filter { it.first == sessionId }.map { it.second }
}
