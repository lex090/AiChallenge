package com.ai.challenge.branch.repository

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class ExposedBranchRepositoryTest {

    private lateinit var repository: ExposedBranchRepository

    @BeforeTest
    fun setUp() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_branch_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        repository = ExposedBranchRepository(database = db)
    }

    @Test
    fun `create and get round-trip`() = runTest {
        val branch = Branch(
            id = BranchId.generate(),
            sessionId = AgentSessionId(value = "s1"),
            name = "main",
            parentBranchId = null,
            isActive = true,
            turnIds = emptyList(),
            createdAt = Clock.System.now(),
        )
        repository.create(branch = branch)

        val result = repository.get(branchId = branch.id)
        assertNotNull(result)
        assertEquals("main", result.name)
        assertTrue(result.isActive)
        assertTrue(result.isMain)
        assertEquals(emptyList(), result.turnIds)
    }

    @Test
    fun `create persists turnIds and get returns them`() = runTest {
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        val branch = Branch(
            id = BranchId.generate(),
            sessionId = AgentSessionId(value = "s1"),
            name = "main",
            parentBranchId = null,
            isActive = true,
            turnIds = listOf(turnId1, turnId2),
            createdAt = Clock.System.now(),
        )
        repository.create(branch = branch)

        val result = repository.get(branchId = branch.id)
        assertNotNull(result)
        assertEquals(listOf(turnId1, turnId2), result.turnIds)
    }

    @Test
    fun `getBySession returns branches for session`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val main = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            name = "main",
            parentBranchId = null,
            isActive = true,
            turnIds = emptyList(),
            createdAt = Clock.System.now(),
        )
        val child = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            name = "experiment",
            parentBranchId = main.id,
            isActive = false,
            turnIds = emptyList(),
            createdAt = Clock.System.now(),
        )
        repository.create(branch = main)
        repository.create(branch = child)

        val result = repository.getBySession(sessionId = sessionId)
        assertEquals(2, result.size)
    }

    @Test
    fun `getMainBranch returns branch with null parentBranchId`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val main = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            name = "main",
            parentBranchId = null,
            isActive = true,
            turnIds = emptyList(),
            createdAt = Clock.System.now(),
        )
        repository.create(branch = main)

        val result = repository.getMainBranch(sessionId = sessionId)
        assertNotNull(result)
        assertEquals(main.id, result.id)
    }

    @Test
    fun `getActiveBranch returns branch with isActive true`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val main = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            name = "main",
            parentBranchId = null,
            isActive = true,
            turnIds = emptyList(),
            createdAt = Clock.System.now(),
        )
        repository.create(branch = main)

        val result = repository.getActiveBranch(sessionId = sessionId)
        assertNotNull(result)
        assertEquals(main.id, result.id)
    }

    @Test
    fun `setActive switches active branch`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val main = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            name = "main",
            parentBranchId = null,
            isActive = true,
            turnIds = emptyList(),
            createdAt = Clock.System.now(),
        )
        val child = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            name = "experiment",
            parentBranchId = main.id,
            isActive = false,
            turnIds = emptyList(),
            createdAt = Clock.System.now(),
        )
        repository.create(branch = main)
        repository.create(branch = child)

        repository.setActive(sessionId = sessionId, branchId = child.id)

        val active = repository.getActiveBranch(sessionId = sessionId)
        assertNotNull(active)
        assertEquals(child.id, active.id)

        val mainAfter = repository.get(branchId = main.id)
        assertNotNull(mainAfter)
        assertEquals(false, mainAfter.isActive)
    }

    @Test
    fun `delete removes branch and its turnIds`() = runTest {
        val turnId1 = TurnId.generate()
        val branch = Branch(
            id = BranchId.generate(),
            sessionId = AgentSessionId(value = "s1"),
            name = "experiment",
            parentBranchId = BranchId.generate(),
            isActive = false,
            turnIds = listOf(turnId1),
            createdAt = Clock.System.now(),
        )
        repository.create(branch = branch)
        repository.delete(branchId = branch.id)

        assertNull(repository.get(branchId = branch.id))
    }

    @Test
    fun `get returns null for unknown id`() = runTest {
        assertNull(repository.get(branchId = BranchId(value = "nonexistent")))
    }

    @Test
    fun `appendTurn adds turn to branch`() = runTest {
        val branch = Branch(
            id = BranchId.generate(),
            sessionId = AgentSessionId(value = "s1"),
            name = "main",
            parentBranchId = null,
            isActive = true,
            turnIds = emptyList(),
            createdAt = Clock.System.now(),
        )
        repository.create(branch = branch)

        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        repository.appendTurn(branchId = branch.id, turnId = turnId1)
        repository.appendTurn(branchId = branch.id, turnId = turnId2)

        val result = repository.get(branchId = branch.id)
        assertNotNull(result)
        assertEquals(listOf(turnId1, turnId2), result.turnIds)
    }

    @Test
    fun `deleteTurnsByBranch clears turnIds`() = runTest {
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        val branch = Branch(
            id = BranchId.generate(),
            sessionId = AgentSessionId(value = "s1"),
            name = "main",
            parentBranchId = null,
            isActive = true,
            turnIds = listOf(turnId1, turnId2),
            createdAt = Clock.System.now(),
        )
        repository.create(branch = branch)

        repository.deleteTurnsByBranch(branchId = branch.id)

        val result = repository.get(branchId = branch.id)
        assertNotNull(result)
        assertEquals(emptyList(), result.turnIds)
    }
}
