package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BranchingContextManagerTest {

    private val sessionId = AgentSessionId(value = "session-1")

    private fun buildManager(repo: InMemoryAgentSessionRepository): BranchingContextManager =
        BranchingContextManager(repository = repo)

    @Test
    fun `prepareContext for main branch with turns`() = runTest {
        val repo = InMemoryAgentSessionRepository()
        val mainBranchId = BranchId.generate()

        repo.addSession(createTestSession(sessionId = sessionId, contextManagementType = ContextManagementType.Branching, activeBranchId = mainBranchId))

        val turn1 = createTestTurn(sessionId = sessionId, userMessage = "user1", assistantMessage = "assistant1")
        val turn2 = createTestTurn(sessionId = sessionId, userMessage = "user2", assistantMessage = "assistant2")

        repo.createBranch(branch = createTestBranch(id = mainBranchId, sessionId = sessionId, parentId = null, turnIds = emptyList()))
        repo.appendTurn(branchId = mainBranchId, turn = turn1)
        repo.appendTurn(branchId = mainBranchId, turn = turn2)

        val manager = buildManager(repo = repo)
        val result = manager.prepareContext(sessionId = sessionId, newMessage = MessageContent(value = "newMessage"))

        assertFalse(result.compressed)
        assertEquals(2, result.originalTurnCount)
        assertEquals(2, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(5, result.messages.size)
        assertEquals(MessageRole.User, result.messages[0].role)
        assertEquals(MessageContent(value = "user1"), result.messages[0].content)
        assertEquals(MessageRole.Assistant, result.messages[1].role)
        assertEquals(MessageContent(value = "assistant1"), result.messages[1].content)
        assertEquals(MessageRole.User, result.messages[2].role)
        assertEquals(MessageContent(value = "user2"), result.messages[2].content)
        assertEquals(MessageRole.Assistant, result.messages[3].role)
        assertEquals(MessageContent(value = "assistant2"), result.messages[3].content)
        assertEquals(MessageRole.User, result.messages[4].role)
        assertEquals(MessageContent(value = "newMessage"), result.messages[4].content)
    }

    @Test
    fun `prepareContext for child branch includes trunk`() = runTest {
        val repo = InMemoryAgentSessionRepository()
        val mainBranchId = BranchId.generate()
        val childBranchId = BranchId.generate()

        repo.addSession(createTestSession(sessionId = sessionId, contextManagementType = ContextManagementType.Branching, activeBranchId = childBranchId))

        val turn1 = createTestTurn(sessionId = sessionId, userMessage = "main-user1", assistantMessage = "main-assistant1")
        val turn2 = createTestTurn(sessionId = sessionId, userMessage = "main-user2", assistantMessage = "main-assistant2")
        val turn3 = createTestTurn(sessionId = sessionId, userMessage = "main-user3", assistantMessage = "main-assistant3")
        val childTurn1 = createTestTurn(sessionId = sessionId, userMessage = "child-user1", assistantMessage = "child-assistant1")

        repo.createBranch(branch = createTestBranch(id = mainBranchId, sessionId = sessionId, parentId = null, turnIds = emptyList()))
        repo.appendTurn(branchId = mainBranchId, turn = turn1)
        repo.appendTurn(branchId = mainBranchId, turn = turn2)
        repo.appendTurn(branchId = mainBranchId, turn = turn3)

        repo.createBranch(branch = createTestBranch(id = childBranchId, sessionId = sessionId, parentId = mainBranchId, turnIds = listOf(turn1.id, turn2.id)))
        repo.appendTurn(branchId = childBranchId, turn = childTurn1)

        val manager = buildManager(repo = repo)
        val result = manager.prepareContext(sessionId = sessionId, newMessage = MessageContent(value = "new"))

        assertEquals(7, result.messages.size)
        assertEquals(MessageContent(value = "main-user1"), result.messages[0].content)
        assertEquals(MessageContent(value = "main-assistant1"), result.messages[1].content)
        assertEquals(MessageContent(value = "main-user2"), result.messages[2].content)
        assertEquals(MessageContent(value = "main-assistant2"), result.messages[3].content)
        assertEquals(MessageContent(value = "child-user1"), result.messages[4].content)
        assertEquals(MessageContent(value = "child-assistant1"), result.messages[5].content)
        assertEquals(MessageContent(value = "new"), result.messages[6].content)
        assertEquals(3, result.originalTurnCount)
        assertEquals(3, result.retainedTurnCount)
    }

    @Test
    fun `prepareContext for empty branch with trunk`() = runTest {
        val repo = InMemoryAgentSessionRepository()
        val mainBranchId = BranchId.generate()
        val childBranchId = BranchId.generate()

        repo.addSession(createTestSession(sessionId = sessionId, contextManagementType = ContextManagementType.Branching, activeBranchId = childBranchId))

        val mainTurn1 = createTestTurn(sessionId = sessionId, userMessage = "main1", assistantMessage = "main-resp1")
        val mainTurn2 = createTestTurn(sessionId = sessionId, userMessage = "main2", assistantMessage = "main-resp2")

        repo.createBranch(branch = createTestBranch(id = mainBranchId, sessionId = sessionId, parentId = null, turnIds = emptyList()))
        repo.appendTurn(branchId = mainBranchId, turn = mainTurn1)
        repo.appendTurn(branchId = mainBranchId, turn = mainTurn2)

        // child branch has mainTurn1 copied in from trunk, no own turns yet
        repo.createBranch(branch = createTestBranch(id = childBranchId, sessionId = sessionId, parentId = mainBranchId, turnIds = listOf(mainTurn1.id)))

        val manager = buildManager(repo = repo)
        val result = manager.prepareContext(sessionId = sessionId, newMessage = MessageContent(value = "new"))

        assertEquals(3, result.messages.size)
        assertEquals(MessageContent(value = "main1"), result.messages[0].content)
        assertEquals(MessageContent(value = "main-resp1"), result.messages[1].content)
        assertEquals(MessageContent(value = "new"), result.messages[2].content)
        assertEquals(1, result.originalTurnCount)
        assertEquals(1, result.retainedTurnCount)
        assertFalse(result.compressed)
    }
}
