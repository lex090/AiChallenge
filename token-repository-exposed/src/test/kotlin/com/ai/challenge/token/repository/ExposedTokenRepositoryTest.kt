package com.ai.challenge.token.repository

import com.ai.challenge.core.SessionId
import com.ai.challenge.core.TokenDetails
import com.ai.challenge.core.TurnId
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
        repository = ExposedTokenRepository(db)
    }

    @Test
    fun `record and getByTurn round-trip`() = runTest {
        val sessionId = SessionId.generate()
        val turnId = TurnId.generate()
        val details = TokenDetails(promptTokens = 100, completionTokens = 50, cachedTokens = 20, cacheWriteTokens = 10, reasoningTokens = 5)

        repository.record(sessionId, turnId, details)

        assertEquals(details, repository.getByTurn(turnId))
    }

    @Test
    fun `getByTurn returns null for unknown turnId`() = runTest {
        assertNull(repository.getByTurn(TurnId.generate()))
    }

    @Test
    fun `getBySession returns tokens for all turns in session`() = runTest {
        val sessionId = SessionId.generate()
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        val t1 = TokenDetails(promptTokens = 10)
        val t2 = TokenDetails(promptTokens = 20)

        repository.record(sessionId, turnId1, t1)
        repository.record(sessionId, turnId2, t2)

        val result = repository.getBySession(sessionId)
        assertEquals(2, result.size)
        assertEquals(t1, result[turnId1])
        assertEquals(t2, result[turnId2])
    }

    @Test
    fun `getBySession does not include tokens from other sessions`() = runTest {
        val session1 = SessionId.generate()
        val session2 = SessionId.generate()

        repository.record(session1, TurnId.generate(), TokenDetails(promptTokens = 10))
        repository.record(session2, TurnId.generate(), TokenDetails(promptTokens = 20))

        val result = repository.getBySession(session1)
        assertEquals(1, result.size)
    }

    @Test
    fun `getSessionTotal returns accumulated tokens`() = runTest {
        val sessionId = SessionId.generate()

        repository.record(sessionId, TurnId.generate(), TokenDetails(promptTokens = 10, completionTokens = 5))
        repository.record(sessionId, TurnId.generate(), TokenDetails(promptTokens = 20, completionTokens = 10))

        val total = repository.getSessionTotal(sessionId)
        assertEquals(30, total.promptTokens)
        assertEquals(15, total.completionTokens)
    }

    @Test
    fun `getSessionTotal returns empty for session with no tokens`() = runTest {
        assertEquals(TokenDetails(), repository.getSessionTotal(SessionId.generate()))
    }
}
