package com.ai.challenge.context

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.BranchRepository
import com.ai.challenge.core.branch.BranchTurnRepository
import com.ai.challenge.core.context.ContextManagementTypeRepository
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactId
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.turn.TurnRepository
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultContextManagerTest {

    private fun turns(sessionId: AgentSessionId, count: Int): List<Turn> =
        (1..count).map { Turn(id = TurnId.generate(), sessionId = sessionId, userMessage = "msg$it", agentResponse = "resp$it", timestamp = Clock.System.now()) }

    private lateinit var fakeCompressor: FakeContextCompressor
    private lateinit var fakeSummaryRepo: InMemorySummaryRepository
    private lateinit var fakeContextManagementRepo: InMemoryContextManagementTypeRepository
    private lateinit var fakeTurnRepo: InMemoryTurnRepository
    private lateinit var fakeFactExtractor: FakeFactExtractor
    private lateinit var fakeFactRepo: InMemoryFactRepository

    @BeforeTest
    fun setup() {
        fakeCompressor = FakeContextCompressor()
        fakeSummaryRepo = InMemorySummaryRepository()
        fakeContextManagementRepo = InMemoryContextManagementTypeRepository()
        fakeTurnRepo = InMemoryTurnRepository()
        fakeFactExtractor = FakeFactExtractor()
        fakeFactRepo = InMemoryFactRepository()
    }

    private fun createManager(): DefaultContextManager =
        DefaultContextManager(
            contextManagementRepository = fakeContextManagementRepo,
            compressor = fakeCompressor,
            summaryRepository = fakeSummaryRepo,
            turnRepository = fakeTurnRepo,
            factExtractor = fakeFactExtractor,
            factRepository = fakeFactRepo,
            branchingContextManager = BranchingContextManager(
                turnRepository = fakeTurnRepo,
                branchRepository = InMemoryBranchRepository(),
                branchTurnRepository = InMemoryBranchTurnRepository(),
            ),
        )

    private suspend fun saveTurns(sessionId: AgentSessionId, turns: List<Turn>) {
        for (turn in turns) {
            fakeTurnRepo.append(turn = turn)
        }
    }

    // --- None tests ---

    @Test
    fun `returns all turns when type is None`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId = sessionId, type = ContextManagementType.None)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 20))
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, newMessage = "new msg")

        assertFalse(result.compressed)
        assertEquals(20, result.originalTurnCount)
        assertEquals(20, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
    }

    // --- SummarizeOnThreshold tests ---

    @Test
    fun `returns all turns when SummarizeOnThreshold and below threshold`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId = sessionId, type = ContextManagementType.SummarizeOnThreshold)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 3))
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, newMessage = "new msg")

        assertFalse(result.compressed)
        assertEquals(3, result.originalTurnCount)
        assertEquals(3, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(7, result.messages.size)
        assertEquals(ContextMessage(role = MessageRole.User, content = "new msg"), result.messages.last())
    }

    @Test
    fun `compresses when SummarizeOnThreshold and at threshold`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId = sessionId, type = ContextManagementType.SummarizeOnThreshold)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 15))
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, newMessage = "new msg")

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
        fakeContextManagementRepo.save(sessionId = sessionId, type = ContextManagementType.SummarizeOnThreshold)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 15))
        val manager = createManager()

        manager.prepareContext(sessionId = sessionId, newMessage = "msg15")
        assertEquals(1, fakeCompressor.callCount)

        fakeTurnRepo.append(turn = Turn(id = TurnId.generate(), sessionId = sessionId, userMessage = "msg16", agentResponse = "resp16", timestamp = Clock.System.now()))
        val result = manager.prepareContext(sessionId = sessionId, newMessage = "msg16")
        assertEquals(1, fakeCompressor.callCount)
        assertTrue(result.compressed)
    }

    @Test
    fun `handles empty history`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId = sessionId, type = ContextManagementType.SummarizeOnThreshold)
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, newMessage = "hello")

        assertFalse(result.compressed)
        assertEquals(0, result.originalTurnCount)
        assertEquals(1, result.messages.size)
        assertEquals(ContextMessage(role = MessageRole.User, content = "hello"), result.messages[0])
    }

    // --- SlidingWindow tests ---

    @Test
    fun `sliding window returns all turns when history is smaller than window`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId = sessionId, type = ContextManagementType.SlidingWindow)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 5))
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, newMessage = "new msg")

        assertFalse(result.compressed)
        assertEquals(5, result.originalTurnCount)
        assertEquals(5, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(11, result.messages.size)
        assertEquals(ContextMessage(role = MessageRole.User, content = "new msg"), result.messages.last())
    }

    @Test
    fun `sliding window retains only last 10 turns when history exceeds window`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId = sessionId, type = ContextManagementType.SlidingWindow)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 15))
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, newMessage = "new msg")

        assertFalse(result.compressed)
        assertEquals(15, result.originalTurnCount)
        assertEquals(10, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(21, result.messages.size)
        assertEquals(MessageRole.User, result.messages.first().role)
        assertEquals("msg6", result.messages.first().content)
        assertEquals(ContextMessage(role = MessageRole.User, content = "new msg"), result.messages.last())
    }

    @Test
    fun `sliding window handles empty history`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId = sessionId, type = ContextManagementType.SlidingWindow)
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, newMessage = "hello")

        assertFalse(result.compressed)
        assertEquals(0, result.originalTurnCount)
        assertEquals(0, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(1, result.messages.size)
        assertEquals(ContextMessage(role = MessageRole.User, content = "hello"), result.messages[0])
    }

    // --- StickyFacts tests ---

    @Test
    fun `stickyFacts extracts facts and includes system message`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId = sessionId, type = ContextManagementType.StickyFacts)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 3))
        fakeFactExtractor.factsToReturn = listOf(
            Fact(id = FactId.generate(), sessionId = AgentSessionId("s1"), category = FactCategory.Goal, key = "goal", value = "Build a bot"),
        )
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, newMessage = "new msg")

        assertTrue(result.compressed)
        assertEquals(3, result.originalTurnCount)
        assertEquals(3, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(MessageRole.System, result.messages.first().role)
        assertTrue(result.messages.first().content.contains("Build a bot"))
        assertEquals(1, fakeFactExtractor.callCount)
    }

    @Test
    fun `stickyFacts retains only last 5 turns`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId = sessionId, type = ContextManagementType.StickyFacts)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 8))
        fakeFactExtractor.factsToReturn = listOf(
            Fact(id = FactId.generate(), sessionId = AgentSessionId("s1"), category = FactCategory.Goal, key = "goal", value = "A goal"),
        )
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, newMessage = "new msg")

        assertTrue(result.compressed)
        assertEquals(8, result.originalTurnCount)
        assertEquals(5, result.retainedTurnCount)
        // system + 5 turns * 2 messages + new user message = 12
        assertEquals(12, result.messages.size)
        assertEquals(ContextMessage(role = MessageRole.User, content = "msg4"), result.messages[1])
    }

    @Test
    fun `stickyFacts with no facts extracted omits system message`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId = sessionId, type = ContextManagementType.StickyFacts)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 2))
        fakeFactExtractor.factsToReturn = emptyList()
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, newMessage = "new msg")

        assertFalse(result.compressed)
        assertEquals(2, result.originalTurnCount)
        assertEquals(2, result.retainedTurnCount)
        // 2 turns * 2 messages + new user message = 5 (no system)
        assertEquals(5, result.messages.size)
        assertEquals(MessageRole.User, result.messages.first().role)
    }

    @Test
    fun `stickyFacts persists extracted facts`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId = sessionId, type = ContextManagementType.StickyFacts)
        val expectedFacts = listOf(
            Fact(id = FactId.generate(), sessionId = AgentSessionId("s1"), category = FactCategory.Decision, key = "db", value = "SQLite"),
        )
        fakeFactExtractor.factsToReturn = expectedFacts
        val manager = createManager()

        manager.prepareContext(sessionId = sessionId, newMessage = "Use SQLite")

        val savedFacts = fakeFactRepo.getBySession(sessionId = sessionId)
        assertEquals(1, savedFacts.size)
        assertEquals("SQLite", savedFacts[0].value)
    }

    @Test
    fun `stickyFacts with empty history returns system message and new message`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId = sessionId, type = ContextManagementType.StickyFacts)
        fakeFactExtractor.factsToReturn = listOf(
            Fact(id = FactId.generate(), sessionId = AgentSessionId("s1"), category = FactCategory.Goal, key = "goal", value = "Start fresh"),
        )
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, newMessage = "hello")

        assertTrue(result.compressed)
        assertEquals(0, result.originalTurnCount)
        assertEquals(0, result.retainedTurnCount)
        assertEquals(2, result.messages.size)
        assertEquals(MessageRole.System, result.messages[0].role)
        assertEquals(ContextMessage(role = MessageRole.User, content = "hello"), result.messages[1])
    }

    @Test
    fun `stickyFacts formats categories in system message`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId = sessionId, type = ContextManagementType.StickyFacts)
        fakeFactExtractor.factsToReturn = listOf(
            Fact(id = FactId.generate(), sessionId = AgentSessionId("s1"), category = FactCategory.Goal, key = "goal", value = "Build bot"),
            Fact(id = FactId.generate(), sessionId = AgentSessionId("s1"), category = FactCategory.Constraint, key = "lang", value = "Kotlin"),
            Fact(id = FactId.generate(), sessionId = AgentSessionId("s1"), category = FactCategory.Preference, key = "style", value = "FP"),
            Fact(id = FactId.generate(), sessionId = AgentSessionId("s1"), category = FactCategory.Decision, key = "db", value = "SQLite"),
            Fact(id = FactId.generate(), sessionId = AgentSessionId("s1"), category = FactCategory.Agreement, key = "deadline", value = "Friday"),
        )
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, newMessage = "msg")

        val systemContent = result.messages.first().content
        assertTrue(systemContent.contains("## Goals"))
        assertTrue(systemContent.contains("- goal: Build bot"))
        assertTrue(systemContent.contains("## Constraints"))
        assertTrue(systemContent.contains("- lang: Kotlin"))
        assertTrue(systemContent.contains("## Preferences"))
        assertTrue(systemContent.contains("- style: FP"))
        assertTrue(systemContent.contains("## Decisions"))
        assertTrue(systemContent.contains("- db: SQLite"))
        assertTrue(systemContent.contains("## Agreements"))
        assertTrue(systemContent.contains("- deadline: Friday"))
    }

    @Test
    fun `stickyFacts passes last assistant response to extractor`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId = sessionId, type = ContextManagementType.StickyFacts)
        saveTurns(sessionId = sessionId, turns = listOf(Turn(id = TurnId.generate(), sessionId = sessionId, userMessage = "hi", agentResponse = "hello there", timestamp = Clock.System.now())))
        fakeFactExtractor.factsToReturn = emptyList()
        val manager = createManager()

        manager.prepareContext(sessionId = sessionId, newMessage = "next msg")

        assertEquals("hello there", fakeFactExtractor.lastAssistantResponse)
        assertEquals("next msg", fakeFactExtractor.lastNewUserMessage)
    }
}

// --- Fakes ---

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
    private val store = mutableListOf<Summary>()

    override suspend fun save(summary: Summary) {
        store.add(summary)
    }

    override suspend fun getBySession(sessionId: AgentSessionId): List<Summary> =
        store.filter { it.sessionId == sessionId }
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

private class FakeFactExtractor : FactExtractor {
    var callCount = 0
        private set
    var lastCurrentFacts: List<Fact> = emptyList()
        private set
    var lastNewUserMessage: String = ""
        private set
    var lastAssistantResponse: String? = null
        private set
    var factsToReturn: List<Fact> = emptyList()

    override suspend fun extract(
        sessionId: AgentSessionId,
        currentFacts: List<Fact>,
        newUserMessage: String,
        lastAssistantResponse: String?,
    ): List<Fact> {
        callCount++
        lastCurrentFacts = currentFacts
        lastNewUserMessage = newUserMessage
        this.lastAssistantResponse = lastAssistantResponse
        return factsToReturn
    }
}

private class InMemoryFactRepository : FactRepository {
    private val store = mutableMapOf<AgentSessionId, List<Fact>>()

    override suspend fun save(facts: List<Fact>) {
        if (facts.isNotEmpty()) {
            store[facts.first().sessionId] = facts
        }
    }

    override suspend fun getBySession(sessionId: AgentSessionId): List<Fact> =
        store[sessionId] ?: emptyList()

    override suspend fun deleteBySession(sessionId: AgentSessionId) {
        store.remove(sessionId)
    }
}
