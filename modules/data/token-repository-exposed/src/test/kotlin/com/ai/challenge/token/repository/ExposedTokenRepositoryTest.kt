package com.ai.challenge.token.repository

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.token.TokenDetails
import com.ai.challenge.core.turn.TurnId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExposedTokenRepositoryTest {

    private lateinit var repository: ExposedTokenRepository

    @BeforeTest
    fun setUp() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_token_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        repository = ExposedTokenRepository(database = db)
    }

    @Test
    fun `record and getByTurn round-trip`() = runTest {
        val sessionId = AgentSessionId.generate()
        val turnId = TurnId.generate()
        val details = TokenDetails(promptTokens = 100, completionTokens = 50, cachedTokens = 20, cacheWriteTokens = 10, reasoningTokens = 5)

        repository.record(sessionId = sessionId, turnId = turnId, details = details)

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
        val t1 = TokenDetails(promptTokens = 10, completionTokens = 0, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0)
        val t2 = TokenDetails(promptTokens = 20, completionTokens = 0, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0)

        repository.record(sessionId = sessionId, turnId = turnId1, details = t1)
        repository.record(sessionId = sessionId, turnId = turnId2, details = t2)

        val result = repository.getBySession(sessionId = sessionId)
        assertEquals(expected = 2, actual = result.size)
        assertEquals(expected = t1, actual = result[turnId1])
        assertEquals(expected = t2, actual = result[turnId2])
    }

    @Test
    fun `getBySession does not include tokens from other sessions`() = runTest {
        val session1 = AgentSessionId.generate()
        val session2 = AgentSessionId.generate()

        repository.record(sessionId = session1, turnId = TurnId.generate(), details = TokenDetails(promptTokens = 10, completionTokens = 0, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0))
        repository.record(sessionId = session2, turnId = TurnId.generate(), details = TokenDetails(promptTokens = 20, completionTokens = 0, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0))

        val result = repository.getBySession(sessionId = session1)
        assertEquals(expected = 1, actual = result.size)
    }

    @Test
    fun `getSessionTotal returns accumulated tokens`() = runTest {
        val sessionId = AgentSessionId.generate()

        repository.record(sessionId = sessionId, turnId = TurnId.generate(), details = TokenDetails(promptTokens = 10, completionTokens = 5, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0))
        repository.record(sessionId = sessionId, turnId = TurnId.generate(), details = TokenDetails(promptTokens = 20, completionTokens = 10, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0))

        val total = repository.getSessionTotal(sessionId = sessionId)
        assertEquals(expected = 30, actual = total.promptTokens)
        assertEquals(expected = 15, actual = total.completionTokens)
    }

    @Test
    fun `getSessionTotal returns empty for session with no tokens`() = runTest {
        assertEquals(expected = TokenDetails(promptTokens = 0, completionTokens = 0, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0), actual = repository.getSessionTotal(sessionId = AgentSessionId.generate()))
    }
}
