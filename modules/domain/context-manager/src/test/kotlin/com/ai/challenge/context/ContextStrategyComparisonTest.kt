package com.ai.challenge.context

import com.ai.challenge.core.Branch
import com.ai.challenge.core.BranchId
import com.ai.challenge.core.BranchRepository
import com.ai.challenge.core.CompressedContext
import com.ai.challenge.core.ContextStrategyType
import com.ai.challenge.core.Fact
import com.ai.challenge.core.FactExtractor
import com.ai.challenge.core.FactRepository
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn
import com.ai.challenge.core.TurnId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextStrategyComparisonTest {

    private val sessionId = SessionId.generate()

    private val scenarioMessages = listOf(
        "I need to build a mobile app for tracking fitness goals.",
        "The app should support iOS and Android platforms.",
        "I want to use React Native for cross-platform development.",
        "The main features are: workout logging, goal setting, and progress charts.",
        "We need user authentication with OAuth2.",
        "The backend should use Node.js with PostgreSQL.",
        "Budget is around $50k and deadline is 3 months.",
        "We should prioritize the workout logging feature first.",
        "Add push notifications for daily reminders.",
        "The design should follow Material Design guidelines.",
        "We need offline support for logging workouts without internet.",
        "Let's also add social features like sharing achievements.",
    )

    private val predefinedFacts = listOf(
        Fact(key = "project_type", value = "mobile fitness app"),
        Fact(key = "platforms", value = "iOS and Android"),
        Fact(key = "framework", value = "React Native"),
        Fact(key = "features", value = "workout logging, goal setting, progress charts, push notifications, social sharing"),
        Fact(key = "auth", value = "OAuth2"),
        Fact(key = "backend", value = "Node.js with PostgreSQL"),
        Fact(key = "budget", value = "$50k"),
        Fact(key = "deadline", value = "3 months"),
        Fact(key = "priority", value = "workout logging first"),
        Fact(key = "design", value = "Material Design"),
        Fact(key = "offline", value = "required for workout logging"),
    )

    private fun buildHistory(messageCount: Int): List<Turn> =
        scenarioMessages.take(messageCount).mapIndexed { index, msg ->
            Turn(
                userMessage = msg,
                agentResponse = "Acknowledged: $msg (response #${index + 1})",
            )
        }

    @Test
    fun `sliding window only retains windowSize turns`() = runTest {
        val manager = SlidingWindowContextManager(windowSize = 5)
        val history = buildHistory(10)

        val context = manager.prepareContext(sessionId, history, "Final question")

        assertEquals(10, context.originalTurnCount)
        assertEquals(5, context.retainedTurnCount)
        assertFalse(context.compressed)
        assertEquals(0, context.summaryCount)
        // 5 retained turns * 2 messages + 1 new user message = 11
        assertEquals(11, context.messages.size)
    }

    @Test
    fun `sliding window retains all when history smaller than window`() = runTest {
        val manager = SlidingWindowContextManager(windowSize = 20)
        val history = buildHistory(5)

        val context = manager.prepareContext(sessionId, history, "Question")

        assertEquals(5, context.originalTurnCount)
        assertEquals(5, context.retainedTurnCount)
        assertEquals(11, context.messages.size)
    }

    @Test
    fun `sticky facts includes system message with facts`() = runTest {
        val factExtractor = FakeFactExtractor(predefinedFacts)
        val factRepository = InMemoryFactRepository()
        val manager = StickyFactsContextManager(
            factExtractor = factExtractor,
            factRepository = factRepository,
            windowSize = 5,
        )
        val history = buildHistory(10)

        val context = manager.prepareContext(sessionId, history, "What's the plan?")

        assertEquals(10, context.originalTurnCount)
        assertEquals(5, context.retainedTurnCount)
        assertTrue(context.compressed)
        // 1 system message + 5 turns * 2 + 1 new message = 12
        assertEquals(12, context.messages.size)
        assertTrue(context.messages.first().content.contains("fitness app"))

        val savedFacts = factRepository.getBySession(sessionId)
        assertEquals(predefinedFacts.size, savedFacts.size)
    }

    @Test
    fun `sticky facts without existing facts still works`() = runTest {
        val factExtractor = FakeFactExtractor(emptyList())
        val factRepository = InMemoryFactRepository()
        val manager = StickyFactsContextManager(
            factExtractor = factExtractor,
            factRepository = factRepository,
            windowSize = 5,
        )
        val history = buildHistory(3)

        val context = manager.prepareContext(sessionId, history, "Hello")

        assertEquals(3, context.retainedTurnCount)
        assertFalse(context.compressed)
        // No system message since no facts; 3 turns * 2 + 1 new = 7
        assertEquals(7, context.messages.size)
    }

    @Test
    fun `branching without active branch uses full history`() = runTest {
        val branchRepository = InMemoryBranchRepository()
        val manager = BranchingContextManager(
            branchRepository = branchRepository,
            windowSize = 5,
        )
        val history = buildHistory(8)

        val context = manager.prepareContext(sessionId, history, "Continue")

        assertEquals(8, context.originalTurnCount)
        assertEquals(5, context.retainedTurnCount)
    }

    @Test
    fun `branching with active branch combines main and branch history`() = runTest {
        val branchRepository = InMemoryBranchRepository()
        val branch = Branch(
            sessionId = sessionId,
            name = "alternative",
            checkpointTurnIndex = 4,
        )
        branchRepository.createBranch(branch)
        branchRepository.setActiveBranch(sessionId, branch.id)

        val branchTurn = Turn(userMessage = "Branch message", agentResponse = "Branch response")
        branchRepository.appendTurnToBranch(branch.id, branchTurn)

        val manager = BranchingContextManager(
            branchRepository = branchRepository,
            windowSize = 20,
        )
        val history = buildHistory(8)

        val context = manager.prepareContext(sessionId, history, "Question on branch")

        // 4 main turns + 1 branch turn = 5 combined
        assertEquals(5, context.originalTurnCount)
        assertEquals(5, context.retainedTurnCount)
        // 5 turns * 2 + 1 new message = 11
        assertEquals(11, context.messages.size)
    }

    @Test
    fun `delegating manager switches between strategies`() = runTest {
        val factExtractor = FakeFactExtractor(predefinedFacts)
        val factRepository = InMemoryFactRepository()
        val branchRepository = InMemoryBranchRepository()

        val managers = mapOf(
            ContextStrategyType.SlidingWindow to SlidingWindowContextManager(windowSize = 5),
            ContextStrategyType.StickyFacts to StickyFactsContextManager(factExtractor, factRepository, windowSize = 5),
            ContextStrategyType.Branching to BranchingContextManager(branchRepository, windowSize = 5),
        )
        val delegating = DelegatingContextManager(managers, ContextStrategyType.SlidingWindow)

        val history = buildHistory(10)

        val slidingResult = delegating.prepareContext(sessionId, history, "Test")
        assertFalse(slidingResult.compressed)
        assertEquals(5, slidingResult.retainedTurnCount)

        delegating.activeStrategy = ContextStrategyType.StickyFacts
        val factsResult = delegating.prepareContext(sessionId, history, "Test")
        assertTrue(factsResult.compressed)
        assertTrue(factsResult.messages.first().content.contains("Known facts"))

        delegating.activeStrategy = ContextStrategyType.Branching
        val branchingResult = delegating.prepareContext(sessionId, history, "Test")
        assertFalse(branchingResult.compressed)
        assertEquals(5, branchingResult.retainedTurnCount)
    }

    @Test
    fun `comparison report across all strategies`() = runTest {
        val factExtractor = FakeFactExtractor(predefinedFacts)
        val factRepository = InMemoryFactRepository()
        val branchRepository = InMemoryBranchRepository()

        data class StrategyMetrics(
            val name: String,
            val contexts: MutableList<CompressedContext> = mutableListOf(),
        ) {
            val totalMessages: Int get() = contexts.sumOf { it.messages.size }
            val avgRetained: Double get() = contexts.map { it.retainedTurnCount }.average()
        }

        val strategies = mapOf(
            "SlidingWindow" to SlidingWindowContextManager(windowSize = 5),
            "StickyFacts" to StickyFactsContextManager(factExtractor, factRepository, windowSize = 5) as com.ai.challenge.core.ContextManager,
            "Branching" to BranchingContextManager(branchRepository, windowSize = 5),
        )

        val metrics = strategies.map { (name, _) -> name to StrategyMetrics(name) }.toMap()

        for (i in scenarioMessages.indices) {
            val history = buildHistory(i)
            val newMessage = scenarioMessages[i]

            for ((name, manager) in strategies) {
                val context = manager.prepareContext(sessionId, history, newMessage)
                metrics[name]!!.contexts.add(context)
            }
        }

        for ((name, m) in metrics) {
            println("Strategy: $name")
            println("  Total messages sent to LLM across ${m.contexts.size} turns: ${m.totalMessages}")
            println("  Average retained turns: ${"%.1f".format(m.avgRetained)}")
            println("  Final context size: ${m.contexts.last().messages.size} messages")
            println()
        }

        // Verify all strategies produced results for all turns
        for ((_, m) in metrics) {
            assertEquals(scenarioMessages.size, m.contexts.size)
        }

        // StickyFacts should have more messages than SlidingWindow (system message with facts)
        val slidingFinal = metrics["SlidingWindow"]!!.contexts.last()
        val factsFinal = metrics["StickyFacts"]!!.contexts.last()
        assertTrue(factsFinal.messages.size >= slidingFinal.messages.size)

        // Verify facts were persisted
        val facts = factRepository.getBySession(sessionId)
        assertEquals(predefinedFacts.size, facts.size)
    }
}

private class FakeFactExtractor(
    private val factsToReturn: List<Fact>,
) : FactExtractor {
    override suspend fun extract(
        history: List<Turn>,
        currentFacts: List<Fact>,
        newMessage: String,
    ): List<Fact> = factsToReturn
}

private class InMemoryFactRepository : FactRepository {
    private val store = mutableMapOf<SessionId, List<Fact>>()

    override suspend fun save(sessionId: SessionId, facts: List<Fact>) {
        store[sessionId] = facts
    }

    override suspend fun getBySession(sessionId: SessionId): List<Fact> =
        store[sessionId] ?: emptyList()
}

private class InMemoryBranchRepository : BranchRepository {
    private val branches = mutableMapOf<BranchId, Branch>()
    private val activeBranches = mutableMapOf<SessionId, BranchId>()
    private val branchTurns = mutableMapOf<BranchId, MutableList<Turn>>()

    override suspend fun createBranch(branch: Branch): BranchId {
        branches[branch.id] = branch
        branchTurns[branch.id] = mutableListOf()
        return branch.id
    }

    override suspend fun getBranches(sessionId: SessionId): List<Branch> =
        branches.values.filter { it.sessionId == sessionId }

    override suspend fun getBranch(branchId: BranchId): Branch? = branches[branchId]

    override suspend fun deleteBranch(branchId: BranchId): Boolean {
        branchTurns.remove(branchId)
        return branches.remove(branchId) != null
    }

    override suspend fun getActiveBranch(sessionId: SessionId): Branch? {
        val activeId = activeBranches[sessionId] ?: return null
        return branches[activeId]
    }

    override suspend fun setActiveBranch(sessionId: SessionId, branchId: BranchId?) {
        if (branchId != null) {
            activeBranches[sessionId] = branchId
        } else {
            activeBranches.remove(sessionId)
        }
    }

    override suspend fun getTurnsForBranch(branchId: BranchId): List<Turn> =
        branchTurns[branchId] ?: emptyList()

    override suspend fun appendTurnToBranch(branchId: BranchId, turn: Turn): TurnId {
        branchTurns.getOrPut(branchId) { mutableListOf() }.add(turn)
        return turn.id
    }
}
