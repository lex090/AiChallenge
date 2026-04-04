package com.ai.challenge.session

import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExposedUsageManagerTest {

    private lateinit var database: Database
    private lateinit var sessionManager: ExposedSessionManager
    private lateinit var usageManager: ExposedUsageManager

    @BeforeTest
    fun setUp() {
        database = Database.connect(
            url = "jdbc:sqlite:/tmp/test_usage_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
            setupConnection = { conn ->
                conn.createStatement().execute("PRAGMA foreign_keys = ON")
            },
        )
        sessionManager = ExposedSessionManager(database)
        usageManager = ExposedUsageManager(database)
    }

    @Test
    fun `record and getByTurn returns stored metrics`() {
        val sessionId = sessionManager.createSession()
        val turn = Turn(userMessage = "hi", agentResponse = "hello")
        val turnId = sessionManager.appendTurn(sessionId, turn)
        val metrics = RequestMetrics(
            tokens = TokenDetails(promptTokens = 100, completionTokens = 50, cachedTokens = 20, cacheWriteTokens = 10, reasoningTokens = 5),
            cost = CostDetails(totalCost = 0.001, upstreamCost = 0.0008, upstreamPromptCost = 0.0005, upstreamCompletionsCost = 0.0003),
        )

        usageManager.record(turnId, metrics)

        assertEquals(metrics, usageManager.getByTurn(turnId))
    }

    @Test
    fun `getByTurn returns null for unknown turnId`() {
        assertNull(usageManager.getByTurn(TurnId.generate()))
    }

    @Test
    fun `getBySession returns metrics for all turns in session`() {
        val sessionId = sessionManager.createSession()
        val turn1 = Turn(userMessage = "a", agentResponse = "b")
        val turn2 = Turn(userMessage = "c", agentResponse = "d")
        val turnId1 = sessionManager.appendTurn(sessionId, turn1)
        val turnId2 = sessionManager.appendTurn(sessionId, turn2)
        val m1 = RequestMetrics(tokens = TokenDetails(promptTokens = 10))
        val m2 = RequestMetrics(tokens = TokenDetails(promptTokens = 20))

        usageManager.record(turnId1, m1)
        usageManager.record(turnId2, m2)

        val result = usageManager.getBySession(sessionId)
        assertEquals(2, result.size)
        assertEquals(m1, result[turnId1])
        assertEquals(m2, result[turnId2])
    }

    @Test
    fun `getBySession does not include metrics from other sessions`() {
        val sessionId1 = sessionManager.createSession()
        val sessionId2 = sessionManager.createSession()
        val turnId1 = sessionManager.appendTurn(sessionId1, Turn(userMessage = "a", agentResponse = "b"))
        val turnId2 = sessionManager.appendTurn(sessionId2, Turn(userMessage = "c", agentResponse = "d"))
        usageManager.record(turnId1, RequestMetrics(tokens = TokenDetails(promptTokens = 10)))
        usageManager.record(turnId2, RequestMetrics(tokens = TokenDetails(promptTokens = 20)))

        val result = usageManager.getBySession(sessionId1)
        assertEquals(1, result.size)
        assertEquals(10, result[turnId1]!!.tokens.promptTokens)
    }

    @Test
    fun `getSessionTotal returns accumulated metrics`() {
        val sessionId = sessionManager.createSession()
        val turnId1 = sessionManager.appendTurn(sessionId, Turn(userMessage = "a", agentResponse = "b"))
        val turnId2 = sessionManager.appendTurn(sessionId, Turn(userMessage = "c", agentResponse = "d"))
        usageManager.record(turnId1, RequestMetrics(
            tokens = TokenDetails(promptTokens = 10, completionTokens = 5),
            cost = CostDetails(totalCost = 0.001),
        ))
        usageManager.record(turnId2, RequestMetrics(
            tokens = TokenDetails(promptTokens = 20, completionTokens = 10),
            cost = CostDetails(totalCost = 0.002),
        ))

        val total = usageManager.getSessionTotal(sessionId)
        assertEquals(30, total.tokens.promptTokens)
        assertEquals(15, total.tokens.completionTokens)
        assertEquals(0.003, total.cost.totalCost, 1e-9)
    }

    @Test
    fun `getSessionTotal returns empty metrics for session with no metrics`() {
        val sessionId = sessionManager.createSession()
        assertEquals(RequestMetrics(), usageManager.getSessionTotal(sessionId))
    }
}
