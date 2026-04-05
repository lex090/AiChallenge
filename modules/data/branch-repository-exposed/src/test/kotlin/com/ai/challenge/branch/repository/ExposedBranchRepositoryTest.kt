package com.ai.challenge.branch.repository

import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedBranchRepositoryTest {

    private lateinit var repo: ExposedBranchRepository

    @BeforeTest
    fun setup() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_branch_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        repo = ExposedBranchRepository(db)
    }

    @Test
    fun `create and retrieve branch`() = runTest {
        val sessionId = SessionId("s1")
        val branch = repo.createBranch(sessionId, "test-branch", checkpointTurnIndex = 5)

        val retrieved = repo.getBranch(branch.id)
        assertNotNull(retrieved)
        assertEquals("test-branch", retrieved.name)
        assertEquals(5, retrieved.checkpointTurnIndex)
        assertEquals(sessionId, retrieved.sessionId)
    }

    @Test
    fun `getBranches returns all branches for session`() = runTest {
        val sessionId = SessionId("s1")
        repo.createBranch(sessionId, "branch1", 3)
        repo.createBranch(sessionId, "branch2", 5)
        repo.createBranch(SessionId("s2"), "other-branch", 1)

        val branches = repo.getBranches(sessionId)
        assertEquals(2, branches.size)
    }

    @Test
    fun `active branch management`() = runTest {
        val sessionId = SessionId("s1")
        assertNull(repo.getActiveBranch(sessionId))

        val branch = repo.createBranch(sessionId, "branch1", 3)
        repo.setActiveBranch(sessionId, branch.id)
        assertEquals(branch.id, repo.getActiveBranch(sessionId))

        repo.setActiveBranch(sessionId, null)
        assertNull(repo.getActiveBranch(sessionId))
    }

    @Test
    fun `append and retrieve branch turns`() = runTest {
        val sessionId = SessionId("s1")
        val branch = repo.createBranch(sessionId, "branch1", 3)

        repo.appendBranchTurn(branch.id, Turn(userMessage = "msg1", agentResponse = "resp1"))
        repo.appendBranchTurn(branch.id, Turn(userMessage = "msg2", agentResponse = "resp2"))

        val turns = repo.getBranchTurns(branch.id)
        assertEquals(2, turns.size)
        assertEquals("msg1", turns[0].userMessage)
        assertEquals("msg2", turns[1].userMessage)
    }

    @Test
    fun `getBranchTree constructs tree correctly`() = runTest {
        val sessionId = SessionId("s1")
        val b1 = repo.createBranch(sessionId, "branch-at-3", 3)
        val b2 = repo.createBranch(sessionId, "branch-at-3-v2", 3)
        val b3 = repo.createBranch(sessionId, "branch-at-7", 7)

        repo.appendBranchTurn(b1.id, Turn(userMessage = "m1", agentResponse = "r1"))
        repo.appendBranchTurn(b1.id, Turn(userMessage = "m2", agentResponse = "r2"))

        val tree = repo.getBranchTree(sessionId, mainTurnCount = 10)
        assertEquals(10, tree.mainTurnCount)
        assertEquals(2, tree.checkpoints.size)

        val cp3 = tree.checkpoints.first { it.turnIndex == 3 }
        assertEquals(2, cp3.branches.size)

        val b1Node = cp3.branches.first { it.branch.id == b1.id }
        assertEquals(2, b1Node.turnCount)

        val cp7 = tree.checkpoints.first { it.turnIndex == 7 }
        assertEquals(1, cp7.branches.size)
        assertEquals(0, cp7.branches[0].turnCount)
    }

    @Test
    fun `getBranchTurns returns empty for branch with no turns`() = runTest {
        val branch = repo.createBranch(SessionId("s1"), "empty-branch", 0)
        assertTrue(repo.getBranchTurns(branch.id).isEmpty())
    }
}
