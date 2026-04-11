package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.context.model.SummaryContent
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.turn.Turn
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultContextManagerTest {

    private val sessionId = AgentSessionId(value = "s1")
    private val mainBranchId = BranchId.generate()

    private fun turns(sessionId: AgentSessionId, count: Int): List<Turn> =
        (1..count).map { createTestTurn(sessionId = sessionId, userMessage = "msg$it", assistantMessage = "resp$it") }

    private lateinit var fakeCompressor: FakeContextCompressor
    private lateinit var fakeSummaryRepo: InMemorySummaryRepository
    private lateinit var fakeRepo: InMemoryAgentSessionRepository
    private lateinit var fakeFactExtractor: FakeFactExtractor
    private lateinit var fakeFactRepo: InMemoryFactRepository

    @BeforeTest
    fun setup() {
        fakeCompressor = FakeContextCompressor()
        fakeSummaryRepo = InMemorySummaryRepository()
        fakeRepo = InMemoryAgentSessionRepository()
        fakeFactExtractor = FakeFactExtractor()
        fakeFactRepo = InMemoryFactRepository()
    }

    private fun setupSession(type: ContextManagementType) {
        val session = createTestSession(sessionId = sessionId, contextManagementType = type)
        fakeRepo.addSession(session)
        // create main branch synchronously not possible, we'll do it in coroutine
    }

    private fun createManager(): DefaultContextManager =
        DefaultContextManager(
            repository = fakeRepo,
            compressor = fakeCompressor,
            summaryRepository = fakeSummaryRepo,
            factExtractor = fakeFactExtractor,
            factRepository = fakeFactRepo,
            branchingContextManager = BranchingContextManager(
                repository = fakeRepo,
            ),
        )

    private suspend fun saveTurns(sessionId: AgentSessionId, turns: List<Turn>) {
        // Ensure main branch exists
        if (fakeRepo.getBranch(branchId = mainBranchId) == null) {
            fakeRepo.createBranch(branch = createTestBranch(id = mainBranchId, sessionId = sessionId, sourceTurnId = null, turnIds = emptyList()))
        }
        for (turn in turns) {
            fakeRepo.appendTurn(branchId = mainBranchId, turn = turn)
        }
    }

    // --- None tests ---

    @Test
    fun `returns all turns when type is None`() = runTest {
        setupSession(type = ContextManagementType.None)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 20))
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "new msg"))

        assertFalse(result.compressed)
        assertEquals(20, result.originalTurnCount)
        assertEquals(20, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
    }

    // --- SummarizeOnThreshold tests ---

    @Test
    fun `returns all turns when SummarizeOnThreshold and below threshold`() = runTest {
        setupSession(type = ContextManagementType.SummarizeOnThreshold)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 3))
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "new msg"))

        assertFalse(result.compressed)
        assertEquals(3, result.originalTurnCount)
        assertEquals(3, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(7, result.messages.size)
        assertEquals(ContextMessage(role = MessageRole.User, content = MessageContent(value = "new msg")), result.messages.last())
    }

    @Test
    fun `compresses when SummarizeOnThreshold and at threshold`() = runTest {
        setupSession(type = ContextManagementType.SummarizeOnThreshold)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 15))
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "new msg"))

        assertTrue(result.compressed)
        assertEquals(15, result.originalTurnCount)
        assertEquals(5, result.retainedTurnCount)
        assertEquals(1, result.summaryCount)
        assertEquals(MessageRole.System, result.messages.first().role)
        assertEquals(1, fakeCompressor.callCount)
    }

    @Test
    fun `reuses existing summary without recompressing during interval`() = runTest {
        setupSession(type = ContextManagementType.SummarizeOnThreshold)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 15))
        val manager = createManager()

        manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "msg15"))
        assertEquals(1, fakeCompressor.callCount)

        val extraTurn = createTestTurn(sessionId = sessionId, userMessage = "msg16", assistantMessage = "resp16")
        fakeRepo.appendTurn(branchId = mainBranchId, turn = extraTurn)
        val result = manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "msg16"))
        assertEquals(1, fakeCompressor.callCount)
        assertTrue(result.compressed)
    }

    @Test
    fun `handles empty history`() = runTest {
        setupSession(type = ContextManagementType.SummarizeOnThreshold)
        // No need to create branch for empty history, but we need it for getTurns
        fakeRepo.createBranch(branch = createTestBranch(id = mainBranchId, sessionId = sessionId, sourceTurnId = null, turnIds = emptyList()))
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "hello"))

        assertFalse(result.compressed)
        assertEquals(0, result.originalTurnCount)
        assertEquals(1, result.messages.size)
        assertEquals(ContextMessage(role = MessageRole.User, content = MessageContent(value = "hello")), result.messages[0])
    }

    // --- SlidingWindow tests ---

    @Test
    fun `sliding window returns all turns when history is smaller than window`() = runTest {
        setupSession(type = ContextManagementType.SlidingWindow)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 5))
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "new msg"))

        assertFalse(result.compressed)
        assertEquals(5, result.originalTurnCount)
        assertEquals(5, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(11, result.messages.size)
        assertEquals(ContextMessage(role = MessageRole.User, content = MessageContent(value = "new msg")), result.messages.last())
    }

    @Test
    fun `sliding window retains only last 10 turns when history exceeds window`() = runTest {
        setupSession(type = ContextManagementType.SlidingWindow)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 15))
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "new msg"))

        assertFalse(result.compressed)
        assertEquals(15, result.originalTurnCount)
        assertEquals(10, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(21, result.messages.size)
        assertEquals(MessageRole.User, result.messages.first().role)
        assertEquals(MessageContent(value = "msg6"), result.messages.first().content)
        assertEquals(ContextMessage(role = MessageRole.User, content = MessageContent(value = "new msg")), result.messages.last())
    }

    @Test
    fun `sliding window handles empty history`() = runTest {
        setupSession(type = ContextManagementType.SlidingWindow)
        fakeRepo.createBranch(branch = createTestBranch(id = mainBranchId, sessionId = sessionId, sourceTurnId = null, turnIds = emptyList()))
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "hello"))

        assertFalse(result.compressed)
        assertEquals(0, result.originalTurnCount)
        assertEquals(0, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(1, result.messages.size)
        assertEquals(ContextMessage(role = MessageRole.User, content = MessageContent(value = "hello")), result.messages[0])
    }

    // --- StickyFacts tests ---

    @Test
    fun `stickyFacts extracts facts and includes system message`() = runTest {
        setupSession(type = ContextManagementType.StickyFacts)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 3))
        fakeFactExtractor.factsToReturn = listOf(
            Fact(sessionId = AgentSessionId(value = "s1"), category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "Build a bot")),
        )
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "new msg"))

        assertTrue(result.compressed)
        assertEquals(3, result.originalTurnCount)
        assertEquals(3, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(MessageRole.System, result.messages.first().role)
        assertTrue(result.messages.first().content.value.contains("Build a bot"))
        assertEquals(1, fakeFactExtractor.callCount)
    }

    @Test
    fun `stickyFacts retains only last 5 turns`() = runTest {
        setupSession(type = ContextManagementType.StickyFacts)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 8))
        fakeFactExtractor.factsToReturn = listOf(
            Fact(sessionId = AgentSessionId(value = "s1"), category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "A goal")),
        )
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "new msg"))

        assertTrue(result.compressed)
        assertEquals(8, result.originalTurnCount)
        assertEquals(5, result.retainedTurnCount)
        // system + 5 turns * 2 messages + new user message = 12
        assertEquals(12, result.messages.size)
        assertEquals(ContextMessage(role = MessageRole.User, content = MessageContent(value = "msg4")), result.messages[1])
    }

    @Test
    fun `stickyFacts with no facts extracted omits system message`() = runTest {
        setupSession(type = ContextManagementType.StickyFacts)
        saveTurns(sessionId = sessionId, turns = turns(sessionId = sessionId, count = 2))
        fakeFactExtractor.factsToReturn = emptyList()
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "new msg"))

        assertFalse(result.compressed)
        assertEquals(2, result.originalTurnCount)
        assertEquals(2, result.retainedTurnCount)
        // 2 turns * 2 messages + new user message = 5 (no system)
        assertEquals(5, result.messages.size)
        assertEquals(MessageRole.User, result.messages.first().role)
    }

    @Test
    fun `stickyFacts persists extracted facts`() = runTest {
        setupSession(type = ContextManagementType.StickyFacts)
        fakeRepo.createBranch(branch = createTestBranch(id = mainBranchId, sessionId = sessionId, sourceTurnId = null, turnIds = emptyList()))
        val expectedFacts = listOf(
            Fact(sessionId = AgentSessionId(value = "s1"), category = FactCategory.Decision, key = FactKey(value = "db"), value = FactValue(value = "SQLite")),
        )
        fakeFactExtractor.factsToReturn = expectedFacts
        val manager = createManager()

        manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "Use SQLite"))

        val savedFacts = fakeFactRepo.getBySession(sessionId = sessionId)
        assertEquals(1, savedFacts.size)
        assertEquals(FactValue(value = "SQLite"), savedFacts[0].value)
    }

    @Test
    fun `stickyFacts with empty history returns system message and new message`() = runTest {
        setupSession(type = ContextManagementType.StickyFacts)
        fakeRepo.createBranch(branch = createTestBranch(id = mainBranchId, sessionId = sessionId, sourceTurnId = null, turnIds = emptyList()))
        fakeFactExtractor.factsToReturn = listOf(
            Fact(sessionId = AgentSessionId(value = "s1"), category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "Start fresh")),
        )
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "hello"))

        assertTrue(result.compressed)
        assertEquals(0, result.originalTurnCount)
        assertEquals(0, result.retainedTurnCount)
        assertEquals(2, result.messages.size)
        assertEquals(MessageRole.System, result.messages[0].role)
        assertEquals(ContextMessage(role = MessageRole.User, content = MessageContent(value = "hello")), result.messages[1])
    }

    @Test
    fun `stickyFacts formats categories in system message`() = runTest {
        setupSession(type = ContextManagementType.StickyFacts)
        fakeRepo.createBranch(branch = createTestBranch(id = mainBranchId, sessionId = sessionId, sourceTurnId = null, turnIds = emptyList()))
        fakeFactExtractor.factsToReturn = listOf(
            Fact(sessionId = AgentSessionId(value = "s1"), category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "Build bot")),
            Fact(sessionId = AgentSessionId(value = "s1"), category = FactCategory.Constraint, key = FactKey(value = "lang"), value = FactValue(value = "Kotlin")),
            Fact(sessionId = AgentSessionId(value = "s1"), category = FactCategory.Preference, key = FactKey(value = "style"), value = FactValue(value = "FP")),
            Fact(sessionId = AgentSessionId(value = "s1"), category = FactCategory.Decision, key = FactKey(value = "db"), value = FactValue(value = "SQLite")),
            Fact(sessionId = AgentSessionId(value = "s1"), category = FactCategory.Agreement, key = FactKey(value = "deadline"), value = FactValue(value = "Friday")),
        )
        val manager = createManager()

        val result = manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "msg"))

        val systemContent = result.messages.first().content.value
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
        setupSession(type = ContextManagementType.StickyFacts)
        val turn = createTestTurn(sessionId = sessionId, userMessage = "hi", assistantMessage = "hello there")
        saveTurns(sessionId = sessionId, turns = listOf(turn))
        fakeFactExtractor.factsToReturn = emptyList()
        val manager = createManager()

        manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "next msg"))

        assertEquals(MessageContent(value = "hello there"), fakeFactExtractor.lastAssistantResponse)
        assertEquals(MessageContent(value = "next msg"), fakeFactExtractor.lastNewUserMessage)
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

    override suspend fun compress(turns: List<Turn>, previousSummary: Summary?): SummaryContent {
        callCount++
        lastPreviousSummary = previousSummary
        lastTurnCount = turns.size
        return SummaryContent(value = "Summary of ${turns.size} turns")
    }
}

private class FakeFactExtractor : FactExtractor {
    var callCount = 0
        private set
    var lastCurrentFacts: List<Fact> = emptyList()
        private set
    var lastNewUserMessage: MessageContent = MessageContent(value = "")
        private set
    var lastAssistantResponse: MessageContent? = null
        private set
    var factsToReturn: List<Fact> = emptyList()

    override suspend fun extract(
        sessionId: AgentSessionId,
        currentFacts: List<Fact>,
        newUserMessage: MessageContent,
        lastAssistantResponse: MessageContent?,
    ): List<Fact> {
        callCount++
        lastCurrentFacts = currentFacts
        lastNewUserMessage = newUserMessage
        this.lastAssistantResponse = lastAssistantResponse
        return factsToReturn
    }
}

