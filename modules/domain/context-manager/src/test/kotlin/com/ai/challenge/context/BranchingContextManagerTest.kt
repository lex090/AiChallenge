package com.ai.challenge.context

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.BranchRepository
import com.ai.challenge.core.branch.BranchTurnRepository
import com.ai.challenge.core.context.ContextManager.PreparedContext.ContextMessage.MessageRole
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.turn.TurnRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock

class BranchingContextManagerTest {

    private val sessionId = AgentSessionId(value = "session-1")

    private fun createBranch(
        id: BranchId,
        parentTurnId: TurnId?,
        isActive: Boolean,
    ): Branch = Branch(
        id = id,
        sessionId = sessionId,
        name = "branch-${id.value}",
        parentTurnId = parentTurnId,
        isActive = isActive,
        createdAt = Clock.System.now(),
    )

    private fun createTurn(userMessage: String, agentResponse: String): Turn =
        Turn(
            id = TurnId.generate(),
            userMessage = userMessage,
            agentResponse = agentResponse,
        )

    private fun buildManager(
        branchRepo: InMemoryBranchRepository,
        branchTurnRepo: InMemoryBranchTurnRepository,
        turnRepo: InMemoryTurnRepository,
    ): BranchingContextManager = BranchingContextManager(
        turnRepository = turnRepo,
        branchRepository = branchRepo,
        branchTurnRepository = branchTurnRepo,
    )

    @Test
    fun `prepareContext for main branch with turns`() = runTest {
        val branchRepo = InMemoryBranchRepository()
        val branchTurnRepo = InMemoryBranchTurnRepository()
        val turnRepo = InMemoryTurnRepository()

        val mainBranchId = BranchId.generate()
        val turn1 = createTurn(userMessage = "user1", agentResponse = "assistant1")
        val turn2 = createTurn(userMessage = "user2", agentResponse = "assistant2")

        branchRepo.create(branch = createBranch(id = mainBranchId, parentTurnId = null, isActive = true))
        turnRepo.append(sessionId = sessionId, turn = turn1)
        turnRepo.append(sessionId = sessionId, turn = turn2)
        branchTurnRepo.append(branchId = mainBranchId, turnId = turn1.id, orderIndex = 0)
        branchTurnRepo.append(branchId = mainBranchId, turnId = turn2.id, orderIndex = 1)

        val manager = buildManager(branchRepo = branchRepo, branchTurnRepo = branchTurnRepo, turnRepo = turnRepo)
        val result = manager.prepareContext(sessionId = sessionId, newMessage = "newMessage")

        assertFalse(result.compressed)
        assertEquals(2, result.originalTurnCount)
        assertEquals(2, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(5, result.messages.size)
        assertEquals(MessageRole.User, result.messages[0].role)
        assertEquals("user1", result.messages[0].content)
        assertEquals(MessageRole.Assistant, result.messages[1].role)
        assertEquals("assistant1", result.messages[1].content)
        assertEquals(MessageRole.User, result.messages[2].role)
        assertEquals("user2", result.messages[2].content)
        assertEquals(MessageRole.Assistant, result.messages[3].role)
        assertEquals("assistant2", result.messages[3].content)
        assertEquals(MessageRole.User, result.messages[4].role)
        assertEquals("newMessage", result.messages[4].content)
    }

    @Test
    fun `prepareContext for child branch includes trunk`() = runTest {
        val branchRepo = InMemoryBranchRepository()
        val branchTurnRepo = InMemoryBranchTurnRepository()
        val turnRepo = InMemoryTurnRepository()

        val mainBranchId = BranchId.generate()
        val childBranchId = BranchId.generate()

        val turn1 = createTurn(userMessage = "main-user1", agentResponse = "main-assistant1")
        val turn2 = createTurn(userMessage = "main-user2", agentResponse = "main-assistant2")
        val turn3 = createTurn(userMessage = "main-user3", agentResponse = "main-assistant3")
        val childTurn1 = createTurn(userMessage = "child-user1", agentResponse = "child-assistant1")

        branchRepo.create(branch = createBranch(id = mainBranchId, parentTurnId = null, isActive = false))
        branchRepo.create(branch = createBranch(id = childBranchId, parentTurnId = turn2.id, isActive = true))

        turnRepo.append(sessionId = sessionId, turn = turn1)
        turnRepo.append(sessionId = sessionId, turn = turn2)
        turnRepo.append(sessionId = sessionId, turn = turn3)
        turnRepo.append(sessionId = sessionId, turn = childTurn1)

        branchTurnRepo.append(branchId = mainBranchId, turnId = turn1.id, orderIndex = 0)
        branchTurnRepo.append(branchId = mainBranchId, turnId = turn2.id, orderIndex = 1)
        branchTurnRepo.append(branchId = mainBranchId, turnId = turn3.id, orderIndex = 2)
        branchTurnRepo.append(branchId = childBranchId, turnId = childTurn1.id, orderIndex = 0)

        val manager = buildManager(branchRepo = branchRepo, branchTurnRepo = branchTurnRepo, turnRepo = turnRepo)
        val result = manager.prepareContext(sessionId = sessionId, newMessage = "new")

        // trunk = turn1 + turn2 (up to parentTurnId), child = childTurn1
        // messages: user1, a1, user2, a2, child-user1, child-a1, new
        assertEquals(7, result.messages.size)
        assertEquals("main-user1", result.messages[0].content)
        assertEquals("main-assistant1", result.messages[1].content)
        assertEquals("main-user2", result.messages[2].content)
        assertEquals("main-assistant2", result.messages[3].content)
        assertEquals("child-user1", result.messages[4].content)
        assertEquals("child-assistant1", result.messages[5].content)
        assertEquals("new", result.messages[6].content)
        assertEquals(3, result.originalTurnCount)
        assertEquals(3, result.retainedTurnCount)
    }

    @Test
    fun `prepareContext for branch of branch includes full path`() = runTest {
        val branchRepo = InMemoryBranchRepository()
        val branchTurnRepo = InMemoryBranchTurnRepository()
        val turnRepo = InMemoryTurnRepository()

        val mainBranchId = BranchId.generate()
        val childBranchId = BranchId.generate()
        val grandchildBranchId = BranchId.generate()

        val mainTurn1 = createTurn(userMessage = "main1", agentResponse = "main-resp1")
        val mainTurn2 = createTurn(userMessage = "main2", agentResponse = "main-resp2")
        val childTurn1 = createTurn(userMessage = "child1", agentResponse = "child-resp1")
        val childTurn2 = createTurn(userMessage = "child2", agentResponse = "child-resp2")
        val grandchildTurn1 = createTurn(userMessage = "grandchild1", agentResponse = "grandchild-resp1")

        branchRepo.create(branch = createBranch(id = mainBranchId, parentTurnId = null, isActive = false))
        branchRepo.create(branch = createBranch(id = childBranchId, parentTurnId = mainTurn1.id, isActive = false))
        branchRepo.create(branch = createBranch(id = grandchildBranchId, parentTurnId = childTurn1.id, isActive = true))

        turnRepo.append(sessionId = sessionId, turn = mainTurn1)
        turnRepo.append(sessionId = sessionId, turn = mainTurn2)
        turnRepo.append(sessionId = sessionId, turn = childTurn1)
        turnRepo.append(sessionId = sessionId, turn = childTurn2)
        turnRepo.append(sessionId = sessionId, turn = grandchildTurn1)

        branchTurnRepo.append(branchId = mainBranchId, turnId = mainTurn1.id, orderIndex = 0)
        branchTurnRepo.append(branchId = mainBranchId, turnId = mainTurn2.id, orderIndex = 1)
        branchTurnRepo.append(branchId = childBranchId, turnId = childTurn1.id, orderIndex = 0)
        branchTurnRepo.append(branchId = childBranchId, turnId = childTurn2.id, orderIndex = 1)
        branchTurnRepo.append(branchId = grandchildBranchId, turnId = grandchildTurn1.id, orderIndex = 0)

        val manager = buildManager(branchRepo = branchRepo, branchTurnRepo = branchTurnRepo, turnRepo = turnRepo)
        val result = manager.prepareContext(sessionId = sessionId, newMessage = "new")

        // grandchild branches from childTurn1, child branches from mainTurn1
        // path: mainTurn1 + childTurn1 + grandchildTurn1 + new
        // messages: main1, main-resp1, child1, child-resp1, grandchild1, grandchild-resp1, new
        assertEquals(7, result.messages.size)
        assertEquals("main1", result.messages[0].content)
        assertEquals("main-resp1", result.messages[1].content)
        assertEquals("child1", result.messages[2].content)
        assertEquals("child-resp1", result.messages[3].content)
        assertEquals("grandchild1", result.messages[4].content)
        assertEquals("grandchild-resp1", result.messages[5].content)
        assertEquals("new", result.messages[6].content)
        assertEquals(3, result.originalTurnCount)
        assertEquals(3, result.retainedTurnCount)
    }

    @Test
    fun `prepareContext for empty branch with trunk`() = runTest {
        val branchRepo = InMemoryBranchRepository()
        val branchTurnRepo = InMemoryBranchTurnRepository()
        val turnRepo = InMemoryTurnRepository()

        val mainBranchId = BranchId.generate()
        val childBranchId = BranchId.generate()

        val mainTurn1 = createTurn(userMessage = "main1", agentResponse = "main-resp1")
        val mainTurn2 = createTurn(userMessage = "main2", agentResponse = "main-resp2")

        branchRepo.create(branch = createBranch(id = mainBranchId, parentTurnId = null, isActive = false))
        branchRepo.create(branch = createBranch(id = childBranchId, parentTurnId = mainTurn1.id, isActive = true))

        turnRepo.append(sessionId = sessionId, turn = mainTurn1)
        turnRepo.append(sessionId = sessionId, turn = mainTurn2)

        branchTurnRepo.append(branchId = mainBranchId, turnId = mainTurn1.id, orderIndex = 0)
        branchTurnRepo.append(branchId = mainBranchId, turnId = mainTurn2.id, orderIndex = 1)
        // child branch has no turns

        val manager = buildManager(branchRepo = branchRepo, branchTurnRepo = branchTurnRepo, turnRepo = turnRepo)
        val result = manager.prepareContext(sessionId = sessionId, newMessage = "new")

        // trunk = mainTurn1 only (child branches from mainTurn1), child branch has no turns
        // messages: main1, main-resp1, new
        assertEquals(3, result.messages.size)
        assertEquals("main1", result.messages[0].content)
        assertEquals("main-resp1", result.messages[1].content)
        assertEquals("new", result.messages[2].content)
        assertEquals(1, result.originalTurnCount)
        assertEquals(1, result.retainedTurnCount)
        assertFalse(result.compressed)
    }
}

