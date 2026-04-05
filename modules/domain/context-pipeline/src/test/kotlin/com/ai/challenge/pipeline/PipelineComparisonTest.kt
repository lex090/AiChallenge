package com.ai.challenge.pipeline

import com.ai.challenge.core.Branch
import com.ai.challenge.core.BranchId
import com.ai.challenge.core.BranchNode
import com.ai.challenge.core.BranchRepository
import com.ai.challenge.core.BranchTree
import com.ai.challenge.core.CheckpointNode
import com.ai.challenge.core.ContextPipeline
import com.ai.challenge.core.ContextStrategyType
import com.ai.challenge.core.Fact
import com.ai.challenge.core.FactExtractor
import com.ai.challenge.core.FactRepository
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn
import com.ai.challenge.core.TurnId
import com.ai.challenge.core.TurnRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PipelineComparisonTest {

    private val sessionId = SessionId.generate()

    private val history = (1..12).map {
        Turn(userMessage = "User message $it", agentResponse = "Agent response $it")
    }

    @Test
    fun `compare all three pipeline presets with 12 messages`() = runTest {
        val factRepository = InMemoryFactRepository()
        val branchRepository = InMemoryBranchRepository()
        val turnRepository = InMemoryTurnRepository()

        val factExtractor = object : FactExtractor {
            override suspend fun extract(
                history: List<Turn>,
                currentFacts: List<Fact>,
                newMessage: String,
            ): List<Fact> = listOf(
                Fact(content = "User is interested in Kotlin"),
                Fact(content = "User prefers functional style"),
            )
        }

        val pipelines = mapOf(
            ContextStrategyType.SlidingWindow to ContextPipelines.slidingWindow(windowSize = 5),
            ContextStrategyType.StickyFacts to ContextPipelines.stickyFacts(
                factExtractor = factExtractor,
                factRepository = factRepository,
                windowSize = 5,
            ),
            ContextStrategyType.Branching to ContextPipelines.branching(
                branchRepository = branchRepository,
                turnRepository = turnRepository,
                windowSize = 5,
            ),
        )

        val manager = PipelineContextManager(pipelines)

        // Sliding Window
        manager.activeStrategy = ContextStrategyType.SlidingWindow
        val slidingResult = manager.prepareContext(sessionId, history, "new question")
        assertEquals(12, slidingResult.originalTurnCount)
        assertEquals(5, slidingResult.retainedTurnCount)
        // 5 turns * 2 messages + 1 new = 11
        assertEquals(11, slidingResult.messages.size)

        // Sticky Facts
        manager.activeStrategy = ContextStrategyType.StickyFacts
        val factsResult = manager.prepareContext(sessionId, history, "new question")
        assertEquals(12, factsResult.originalTurnCount)
        assertEquals(5, factsResult.retainedTurnCount)
        // system (facts) + 5 turns * 2 + 1 new = 12
        assertEquals(12, factsResult.messages.size)
        assertTrue(factsResult.messages[0].content.contains("Kotlin"))

        // Branching (no active branch => same as sliding window)
        manager.activeStrategy = ContextStrategyType.Branching
        val branchResult = manager.prepareContext(sessionId, history, "new question")
        assertEquals(12, branchResult.originalTurnCount)
        assertEquals(5, branchResult.retainedTurnCount)
        assertEquals(11, branchResult.messages.size)

        // Print report
        println("=== Pipeline Comparison Report ===")
        println("History size: ${history.size} turns")
        println()
        println("SlidingWindow: ${slidingResult.retainedTurnCount} retained, ${slidingResult.messages.size} messages")
        println("StickyFacts:   ${factsResult.retainedTurnCount} retained, ${factsResult.messages.size} messages (includes facts system msg)")
        println("Branching:     ${branchResult.retainedTurnCount} retained, ${branchResult.messages.size} messages (no active branch)")
    }

    @Test
    fun `branching pipeline with active branch replaces history`() = runTest {
        val branchRepository = InMemoryBranchRepository()
        val turnRepository = InMemoryTurnRepository()
        val branchId = BranchId.generate()

        val branch = Branch(
            id = branchId,
            sessionId = sessionId,
            name = "test-branch",
            checkpointTurnIndex = 3,
        )
        branchRepository.branches[branchId] = branch
        branchRepository.activeBranches[sessionId] = branchId
        branchRepository.branchTurns[branchId] = mutableListOf(
            Turn(userMessage = "branch msg 1", agentResponse = "branch resp 1"),
            Turn(userMessage = "branch msg 2", agentResponse = "branch resp 2"),
        )

        val pipeline = ContextPipelines.branching(branchRepository, turnRepository, windowSize = 10)
        val manager = PipelineContextManager(
            mapOf(ContextStrategyType.Branching to pipeline)
        )
        manager.activeStrategy = ContextStrategyType.Branching

        val result = manager.prepareContext(sessionId, history, "branch question")

        // 3 main turns + 2 branch turns = 5 retained (under window of 10)
        assertEquals(5, result.retainedTurnCount)
        assertEquals(branchId, pipeline.execute(
            com.ai.challenge.core.ContextState(sessionId, history, "test")
        ).activeBranchId)
    }
}

// In-memory test doubles

private class InMemoryFactRepository : FactRepository {
    val facts = mutableMapOf<SessionId, List<Fact>>()

    override suspend fun getBySession(sessionId: SessionId): List<Fact> =
        facts[sessionId] ?: emptyList()

    override suspend fun save(sessionId: SessionId, facts: List<Fact>) {
        this.facts[sessionId] = facts
    }
}

private class InMemoryBranchRepository : BranchRepository {
    val branches = mutableMapOf<BranchId, Branch>()
    val activeBranches = mutableMapOf<SessionId, BranchId>()
    val branchTurns = mutableMapOf<BranchId, MutableList<Turn>>()

    override suspend fun createBranch(sessionId: SessionId, name: String, checkpointTurnIndex: Int): Branch {
        val branch = Branch(sessionId = sessionId, name = name, checkpointTurnIndex = checkpointTurnIndex)
        branches[branch.id] = branch
        return branch
    }

    override suspend fun getBranch(branchId: BranchId): Branch? = branches[branchId]
    override suspend fun getBranches(sessionId: SessionId): List<Branch> =
        branches.values.filter { it.sessionId == sessionId }

    override suspend fun getActiveBranch(sessionId: SessionId): BranchId? = activeBranches[sessionId]
    override suspend fun setActiveBranch(sessionId: SessionId, branchId: BranchId?) {
        if (branchId == null) activeBranches.remove(sessionId)
        else activeBranches[sessionId] = branchId
    }

    override suspend fun getBranchTree(sessionId: SessionId, mainTurnCount: Int): BranchTree {
        val sessionBranches = getBranches(sessionId)
        val checkpoints = sessionBranches.groupBy { it.checkpointTurnIndex }.map { (idx, brs) ->
            CheckpointNode(idx, brs.map { BranchNode(it, branchTurns[it.id]?.size ?: 0) })
        }
        return BranchTree(sessionId, mainTurnCount, checkpoints)
    }

    override suspend fun appendBranchTurn(branchId: BranchId, turn: Turn): TurnId {
        branchTurns.getOrPut(branchId) { mutableListOf() }.add(turn)
        return turn.id
    }

    override suspend fun getBranchTurns(branchId: BranchId): List<Turn> =
        branchTurns[branchId] ?: emptyList()
}

private class InMemoryTurnRepository : TurnRepository {
    override suspend fun append(sessionId: SessionId, turn: Turn): TurnId = turn.id
    override suspend fun getBySession(sessionId: SessionId, limit: Int?): List<Turn> = emptyList()
    override suspend fun get(turnId: TurnId): Turn? = null
}
