package com.ai.challenge.token.repository

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.token.TokenDetails
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

class ExposedTokenRepositoryTest {

    private lateinit var repository: ExposedTokenRepository
    private lateinit var turnRepository: FakeTurnRepository

    @BeforeTest
    fun setUp() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_token_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        turnRepository = FakeTurnRepository()
        repository = ExposedTokenRepository(database = db, turnRepository = turnRepository)
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
        val details = TokenDetails(promptTokens = 100, completionTokens = 50, cachedTokens = 20, cacheWriteTokens = 10, reasoningTokens = 5)

        repository.record(turnId = turnId, details = details)

        assertEquals(expected = details, actual = repository.getByTurn(turnId = turnId))
    }

    @Test
    fun `getByTurn returns null for unknown turnId`() = runTest {
        assertNull(actual = repository.getByTurn(turnId = TurnId.generate()))
    }

    @Test
    fun `getBySession returns tokens for all turns in session`() = runTest {
        val sessionId = AgentSessionId.generate()
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        createTurn(sessionId = sessionId, turnId = turnId1)
        createTurn(sessionId = sessionId, turnId = turnId2)
        val t1 = TokenDetails(promptTokens = 10, completionTokens = 0, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0)
        val t2 = TokenDetails(promptTokens = 20, completionTokens = 0, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0)

        repository.record(turnId = turnId1, details = t1)
        repository.record(turnId = turnId2, details = t2)

        val result = repository.getBySession(sessionId = sessionId)
        assertEquals(expected = 2, actual = result.size)
        assertEquals(expected = t1, actual = result[turnId1])
        assertEquals(expected = t2, actual = result[turnId2])
    }

    @Test
    fun `getBySession does not include tokens from other sessions`() = runTest {
        val session1 = AgentSessionId.generate()
        val session2 = AgentSessionId.generate()
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        createTurn(sessionId = session1, turnId = turnId1)
        createTurn(sessionId = session2, turnId = turnId2)

        repository.record(turnId = turnId1, details = TokenDetails(promptTokens = 10, completionTokens = 0, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0))
        repository.record(turnId = turnId2, details = TokenDetails(promptTokens = 20, completionTokens = 0, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0))

        val result = repository.getBySession(sessionId = session1)
        assertEquals(expected = 1, actual = result.size)
    }

    @Test
    fun `getSessionTotal returns accumulated tokens`() = runTest {
        val sessionId = AgentSessionId.generate()
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        createTurn(sessionId = sessionId, turnId = turnId1)
        createTurn(sessionId = sessionId, turnId = turnId2)

        repository.record(turnId = turnId1, details = TokenDetails(promptTokens = 10, completionTokens = 5, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0))
        repository.record(turnId = turnId2, details = TokenDetails(promptTokens = 20, completionTokens = 10, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0))

        val total = repository.getSessionTotal(sessionId = sessionId)
        assertEquals(expected = 30, actual = total.promptTokens)
        assertEquals(expected = 15, actual = total.completionTokens)
    }

    @Test
    fun `getSessionTotal returns empty for session with no tokens`() = runTest {
        assertEquals(expected = TokenDetails(promptTokens = 0, completionTokens = 0, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0), actual = repository.getSessionTotal(sessionId = AgentSessionId.generate()))
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
