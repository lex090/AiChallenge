package com.ai.challenge.branch.turn.repository

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.turn.TurnId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExposedBranchTurnRepositoryTest {

    private lateinit var repository: ExposedBranchTurnRepository

    @BeforeTest
    fun setUp() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_branch_turn_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        repository = ExposedBranchTurnRepository(database = db)
    }

    @Test
    fun `append and getTurnIds round-trip`() = runTest {
        val branchId = BranchId(value = "b1")
        repository.append(branchId = branchId, turnId = TurnId(value = "t1"), orderIndex = 0)
        repository.append(branchId = branchId, turnId = TurnId(value = "t2"), orderIndex = 1)

        val result = repository.getTurnIds(branchId = branchId)
        assertEquals(listOf(TurnId(value = "t1"), TurnId(value = "t2")), result)
    }

    @Test
    fun `getTurnIds returns ordered by orderIndex`() = runTest {
        val branchId = BranchId(value = "b1")
        repository.append(branchId = branchId, turnId = TurnId(value = "t2"), orderIndex = 1)
        repository.append(branchId = branchId, turnId = TurnId(value = "t1"), orderIndex = 0)

        val result = repository.getTurnIds(branchId = branchId)
        assertEquals(listOf(TurnId(value = "t1"), TurnId(value = "t2")), result)
    }

    @Test
    fun `findBranchByTurnId returns correct branch`() = runTest {
        val branchId = BranchId(value = "b1")
        repository.append(branchId = branchId, turnId = TurnId(value = "t1"), orderIndex = 0)

        val result = repository.findBranchByTurnId(turnId = TurnId(value = "t1"))
        assertEquals(BranchId(value = "b1"), result)
    }

    @Test
    fun `findBranchByTurnId returns null for unknown turn`() = runTest {
        assertNull(repository.findBranchByTurnId(turnId = TurnId(value = "nonexistent")))
    }

    @Test
    fun `getMaxOrderIndex returns max index`() = runTest {
        val branchId = BranchId(value = "b1")
        repository.append(branchId = branchId, turnId = TurnId(value = "t1"), orderIndex = 0)
        repository.append(branchId = branchId, turnId = TurnId(value = "t2"), orderIndex = 1)

        assertEquals(1, repository.getMaxOrderIndex(branchId = branchId))
    }

    @Test
    fun `getMaxOrderIndex returns null for empty branch`() = runTest {
        assertNull(repository.getMaxOrderIndex(branchId = BranchId(value = "empty")))
    }

    @Test
    fun `deleteByBranch removes all mappings`() = runTest {
        val branchId = BranchId(value = "b1")
        repository.append(branchId = branchId, turnId = TurnId(value = "t1"), orderIndex = 0)
        repository.append(branchId = branchId, turnId = TurnId(value = "t2"), orderIndex = 1)

        repository.deleteByBranch(branchId = branchId)

        assertEquals(emptyList(), repository.getTurnIds(branchId = branchId))
    }

    @Test
    fun `getTurnIds returns empty for unknown branch`() = runTest {
        assertEquals(emptyList(), repository.getTurnIds(branchId = BranchId(value = "unknown")))
    }
}
