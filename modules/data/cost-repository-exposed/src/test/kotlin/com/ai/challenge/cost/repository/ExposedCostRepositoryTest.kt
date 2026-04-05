package com.ai.challenge.cost.repository

import com.ai.challenge.core.metrics.CostDetails
import com.ai.challenge.core.session.SessionId
import com.ai.challenge.core.turn.TurnId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExposedCostRepositoryTest {

    private lateinit var repository: ExposedCostRepository

    @BeforeTest
    fun setUp() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_cost_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        repository = ExposedCostRepository(db)
    }

    @Test
    fun `record and getByTurn round-trip`() = runTest {
        val sessionId = SessionId.generate()
        val turnId = TurnId.generate()
        val details = CostDetails(totalCost = 0.001, upstreamCost = 0.0008, upstreamPromptCost = 0.0005, upstreamCompletionsCost = 0.0003)

        repository.record(sessionId, turnId, details)

        assertEquals(details, repository.getByTurn(turnId))
    }

    @Test
    fun `getByTurn returns null for unknown turnId`() = runTest {
        assertNull(repository.getByTurn(TurnId.generate()))
    }

    @Test
    fun `getBySession returns costs for all turns in session`() = runTest {
        val sessionId = SessionId.generate()
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        val c1 = CostDetails(totalCost = 0.001)
        val c2 = CostDetails(totalCost = 0.002)

        repository.record(sessionId, turnId1, c1)
        repository.record(sessionId, turnId2, c2)

        val result = repository.getBySession(sessionId)
        assertEquals(2, result.size)
        assertEquals(c1, result[turnId1])
        assertEquals(c2, result[turnId2])
    }

    @Test
    fun `getBySession does not include costs from other sessions`() = runTest {
        val session1 = SessionId.generate()
        val session2 = SessionId.generate()

        repository.record(session1, TurnId.generate(), CostDetails(totalCost = 0.001))
        repository.record(session2, TurnId.generate(), CostDetails(totalCost = 0.002))

        val result = repository.getBySession(session1)
        assertEquals(1, result.size)
    }

    @Test
    fun `getSessionTotal returns accumulated costs`() = runTest {
        val sessionId = SessionId.generate()

        repository.record(sessionId, TurnId.generate(), CostDetails(totalCost = 0.001))
        repository.record(sessionId, TurnId.generate(), CostDetails(totalCost = 0.002))

        val total = repository.getSessionTotal(sessionId)
        assertEquals(0.003, total.totalCost, 1e-9)
    }

    @Test
    fun `getSessionTotal returns empty for session with no costs`() = runTest {
        assertEquals(CostDetails(), repository.getSessionTotal(SessionId.generate()))
    }
}
