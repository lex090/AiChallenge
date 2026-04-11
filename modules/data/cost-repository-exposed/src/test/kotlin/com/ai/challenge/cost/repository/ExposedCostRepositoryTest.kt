package com.ai.challenge.cost.repository

import com.ai.challenge.core.cost.CostDetails
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.turn.TurnRepository
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock

class ExposedCostRepositoryTest {

    private lateinit var repository: ExposedCostRepository
    private lateinit var turnRepository: FakeTurnRepository

    @BeforeTest
    fun setUp() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_cost_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        turnRepository = FakeTurnRepository()
        repository = ExposedCostRepository(database = db, turnRepository = turnRepository)
    }

    private fun createTurn(sessionId: AgentSessionId, turnId: TurnId): Turn {
        val turn = Turn(
            id = turnId,
            sessionId = sessionId,
            userMessage = "test",
            agentResponse = "test",
            timestamp = Clock.System.now(),
        )
        turnRepository.store(turn = turn)
        return turn
    }

    @Test
    fun `record and getByTurn round-trip`() = runTest {
        val sessionId = AgentSessionId.generate()
        val turnId = TurnId.generate()
        createTurn(sessionId = sessionId, turnId = turnId)
        val details = CostDetails(totalCost = 0.001, upstreamCost = 0.0008, upstreamPromptCost = 0.0005, upstreamCompletionsCost = 0.0003)

        repository.record(turnId = turnId, details = details)

        assertEquals(expected = details, actual = repository.getByTurn(turnId = turnId))
    }

    @Test
    fun `getByTurn returns null for unknown turnId`() = runTest {
        assertNull(actual = repository.getByTurn(turnId = TurnId.generate()))
    }

    @Test
    fun `getBySession returns costs for all turns in session`() = runTest {
        val sessionId = AgentSessionId.generate()
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        createTurn(sessionId = sessionId, turnId = turnId1)
        createTurn(sessionId = sessionId, turnId = turnId2)
        val c1 = CostDetails(totalCost = 0.001, upstreamCost = 0.0, upstreamPromptCost = 0.0, upstreamCompletionsCost = 0.0)
        val c2 = CostDetails(totalCost = 0.002, upstreamCost = 0.0, upstreamPromptCost = 0.0, upstreamCompletionsCost = 0.0)

        repository.record(turnId = turnId1, details = c1)
        repository.record(turnId = turnId2, details = c2)

        val result = repository.getBySession(sessionId = sessionId)
        assertEquals(expected = 2, actual = result.size)
        assertEquals(expected = c1, actual = result[turnId1])
        assertEquals(expected = c2, actual = result[turnId2])
    }

    @Test
    fun `getBySession does not include costs from other sessions`() = runTest {
        val session1 = AgentSessionId.generate()
        val session2 = AgentSessionId.generate()
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        createTurn(sessionId = session1, turnId = turnId1)
        createTurn(sessionId = session2, turnId = turnId2)

        repository.record(turnId = turnId1, details = CostDetails(totalCost = 0.001, upstreamCost = 0.0, upstreamPromptCost = 0.0, upstreamCompletionsCost = 0.0))
        repository.record(turnId = turnId2, details = CostDetails(totalCost = 0.002, upstreamCost = 0.0, upstreamPromptCost = 0.0, upstreamCompletionsCost = 0.0))

        val result = repository.getBySession(sessionId = session1)
        assertEquals(expected = 1, actual = result.size)
    }

    @Test
    fun `getSessionTotal returns accumulated costs`() = runTest {
        val sessionId = AgentSessionId.generate()
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        createTurn(sessionId = sessionId, turnId = turnId1)
        createTurn(sessionId = sessionId, turnId = turnId2)

        repository.record(turnId = turnId1, details = CostDetails(totalCost = 0.001, upstreamCost = 0.0, upstreamPromptCost = 0.0, upstreamCompletionsCost = 0.0))
        repository.record(turnId = turnId2, details = CostDetails(totalCost = 0.002, upstreamCost = 0.0, upstreamPromptCost = 0.0, upstreamCompletionsCost = 0.0))

        val total = repository.getSessionTotal(sessionId = sessionId)
        assertEquals(expected = 0.003, actual = total.totalCost, absoluteTolerance = 1e-9)
    }

    @Test
    fun `getSessionTotal returns empty for session with no costs`() = runTest {
        assertEquals(expected = CostDetails(totalCost = 0.0, upstreamCost = 0.0, upstreamPromptCost = 0.0, upstreamCompletionsCost = 0.0), actual = repository.getSessionTotal(sessionId = AgentSessionId.generate()))
    }
}

private class FakeTurnRepository : TurnRepository {
    private val turns = ConcurrentHashMap<TurnId, Turn>()

    fun store(turn: Turn) {
        turns[turn.id] = turn
    }

    override suspend fun append(turn: Turn): TurnId {
        turns[turn.id] = turn
        return turn.id
    }

    override suspend fun getBySession(sessionId: AgentSessionId, limit: Int?): List<Turn> {
        val all = turns.values.filter { it.sessionId == sessionId }.sortedBy { it.timestamp }
        return if (limit != null && all.size > limit) all.takeLast(limit) else all
    }

    override suspend fun get(turnId: TurnId): Turn? = turns[turnId]
}
