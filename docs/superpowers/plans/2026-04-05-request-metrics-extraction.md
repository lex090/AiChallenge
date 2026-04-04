# Request Metrics Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract token usage from chat session entities into a separate `RequestMetrics` domain, add cached tokens / reasoning tokens / cost tracking, and update the UI to display the full metrics.

**Architecture:** Replace `TokenUsage` with three domain value objects (`TokenDetails`, `CostDetails`, `RequestMetrics`) stored in a separate `request_metrics` table managed by `UsageManager`. `Turn` is cleaned of all metrics fields and gets a `TurnId`. The UI looks up metrics by `turnId` from a `Map<TurnId, RequestMetrics>` in store state.

**Tech Stack:** Kotlin, Exposed (SQL), MVIKotlin, Compose Multiplatform, Ktor MockEngine, kotlin-test

---

## File Map

### session-storage module

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `src/main/kotlin/com/ai/challenge/session/TurnId.kt` | Value class wrapping UUID string |
| Create | `src/main/kotlin/com/ai/challenge/session/TokenDetails.kt` | Token counts value object with `plus` |
| Create | `src/main/kotlin/com/ai/challenge/session/CostDetails.kt` | Cost breakdown value object with `plus` |
| Create | `src/main/kotlin/com/ai/challenge/session/RequestMetrics.kt` | Composite of TokenDetails + CostDetails with `plus` |
| Create | `src/main/kotlin/com/ai/challenge/session/UsageManager.kt` | Interface for metrics storage |
| Create | `src/main/kotlin/com/ai/challenge/session/ExposedUsageManager.kt` | Exposed implementation + `RequestMetricsTable` |
| Create | `src/main/kotlin/com/ai/challenge/session/InMemoryUsageManager.kt` | In-memory implementation for tests |
| Modify | `src/main/kotlin/com/ai/challenge/session/Turn.kt` | Add `id: TurnId`, remove `tokenUsage` |
| Modify | `src/main/kotlin/com/ai/challenge/session/AgentSession.kt` | No changes needed (uses Turn) |
| Modify | `src/main/kotlin/com/ai/challenge/session/AgentSessionManager.kt` | `appendTurn` returns `TurnId` |
| Modify | `src/main/kotlin/com/ai/challenge/session/ExposedSessionManager.kt` | Add `id` column to TurnsTable, remove token columns, return `TurnId` |
| Modify | `src/main/kotlin/com/ai/challenge/session/InMemorySessionManager.kt` | Generate `TurnId`, return from `appendTurn` |
| Delete | `src/main/kotlin/com/ai/challenge/session/TokenUsage.kt` | Replaced by TokenDetails + CostDetails + RequestMetrics |
| Create | `src/test/kotlin/com/ai/challenge/session/RequestMetricsTest.kt` | Unit tests for value objects |
| Create | `src/test/kotlin/com/ai/challenge/session/ExposedUsageManagerTest.kt` | Integration tests for ExposedUsageManager |

### llm-service module

| Action | File | Responsibility |
|--------|------|---------------|
| Modify | `src/main/kotlin/com/ai/challenge/llm/model/ChatResponse.kt` | Add PromptTokensDetails, CompletionTokensDetails, CostDetails; extend Usage and ChatResponse |
| Create | `src/test/kotlin/com/ai/challenge/llm/model/ChatResponseParsingTest.kt` | JSON parsing tests for new fields |

### ai-agent module

| Action | File | Responsibility |
|--------|------|---------------|
| Modify | `src/main/kotlin/com/ai/challenge/agent/AgentResponse.kt` | Replace `tokenUsage: TokenUsage` with `turnId: TurnId` + `metrics: RequestMetrics` |
| Modify | `src/main/kotlin/com/ai/challenge/agent/Agent.kt` | No change (returns `AgentResponse`) |
| Modify | `src/main/kotlin/com/ai/challenge/agent/OpenRouterAgent.kt` | Add `usageManager` param, mapping function, two-write coordination |
| Modify | `src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt` | Update for new `AgentResponse` shape, add `InMemoryUsageManager`, test full metrics mapping |

### compose-ui module

| Action | File | Responsibility |
|--------|------|---------------|
| Modify | `src/main/kotlin/com/ai/challenge/ui/model/UiMessage.kt` | Replace `tokenUsage` with `turnId: TurnId?` |
| Modify | `src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt` | Replace `sessionTokens` with `turnMetrics` + `sessionMetrics` |
| Modify | `src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt` | Update Msg types, executor, reducer |
| Modify | `src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt` | Update UI to show full metrics from map lookup |
| Modify | `src/main/kotlin/com/ai/challenge/ui/chat/ChatComponent.kt` | Pass `usageManager` through to store factory |
| Modify | `src/main/kotlin/com/ai/challenge/ui/di/AppModule.kt` | Register `UsageManager` in Koin |
| Modify | `src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt` | Rewrite for new state shape, FakeAgent with TurnId, InMemoryUsageManager |

---

## Task 1: Domain Value Objects (session-storage)

**Files:**
- Create: `session-storage/src/main/kotlin/com/ai/challenge/session/TurnId.kt`
- Create: `session-storage/src/main/kotlin/com/ai/challenge/session/TokenDetails.kt`
- Create: `session-storage/src/main/kotlin/com/ai/challenge/session/CostDetails.kt`
- Create: `session-storage/src/main/kotlin/com/ai/challenge/session/RequestMetrics.kt`
- Create: `session-storage/src/test/kotlin/com/ai/challenge/session/RequestMetricsTest.kt`

- [ ] **Step 1: Write failing tests for value objects**

Create `session-storage/src/test/kotlin/com/ai/challenge/session/RequestMetricsTest.kt`:

```kotlin
package com.ai.challenge.session

import kotlin.test.Test
import kotlin.test.assertEquals

class TurnIdTest {
    @Test
    fun `generate creates unique TurnIds`() {
        val id1 = TurnId.generate()
        val id2 = TurnId.generate()
        assert(id1 != id2)
    }
}

class TokenDetailsTest {
    @Test
    fun `totalTokens is sum of prompt and completion`() {
        val details = TokenDetails(promptTokens = 100, completionTokens = 50)
        assertEquals(150, details.totalTokens)
    }

    @Test
    fun `plus accumulates all fields`() {
        val a = TokenDetails(promptTokens = 10, completionTokens = 5, cachedTokens = 3, cacheWriteTokens = 2, reasoningTokens = 1)
        val b = TokenDetails(promptTokens = 20, completionTokens = 10, cachedTokens = 6, cacheWriteTokens = 4, reasoningTokens = 2)
        val sum = a + b
        assertEquals(TokenDetails(promptTokens = 30, completionTokens = 15, cachedTokens = 9, cacheWriteTokens = 6, reasoningTokens = 3), sum)
    }

    @Test
    fun `default values are all zero`() {
        val details = TokenDetails()
        assertEquals(0, details.promptTokens)
        assertEquals(0, details.completionTokens)
        assertEquals(0, details.cachedTokens)
        assertEquals(0, details.cacheWriteTokens)
        assertEquals(0, details.reasoningTokens)
        assertEquals(0, details.totalTokens)
    }
}

class CostDetailsTest {
    @Test
    fun `plus accumulates all fields`() {
        val a = CostDetails(totalCost = 0.001, upstreamCost = 0.0008, upstreamPromptCost = 0.0005, upstreamCompletionsCost = 0.0003)
        val b = CostDetails(totalCost = 0.002, upstreamCost = 0.0016, upstreamPromptCost = 0.001, upstreamCompletionsCost = 0.0006)
        val sum = a + b
        assertEquals(0.003, sum.totalCost, 1e-9)
        assertEquals(0.0024, sum.upstreamCost, 1e-9)
        assertEquals(0.0015, sum.upstreamPromptCost, 1e-9)
        assertEquals(0.0009, sum.upstreamCompletionsCost, 1e-9)
    }

    @Test
    fun `default values are all zero`() {
        val cost = CostDetails()
        assertEquals(0.0, cost.totalCost)
        assertEquals(0.0, cost.upstreamCost)
        assertEquals(0.0, cost.upstreamPromptCost)
        assertEquals(0.0, cost.upstreamCompletionsCost)
    }
}

class RequestMetricsTest {
    @Test
    fun `plus accumulates tokens and cost`() {
        val a = RequestMetrics(
            tokens = TokenDetails(promptTokens = 10, completionTokens = 5),
            cost = CostDetails(totalCost = 0.001),
        )
        val b = RequestMetrics(
            tokens = TokenDetails(promptTokens = 20, completionTokens = 10),
            cost = CostDetails(totalCost = 0.002),
        )
        val sum = a + b
        assertEquals(30, sum.tokens.promptTokens)
        assertEquals(15, sum.tokens.completionTokens)
        assertEquals(0.003, sum.cost.totalCost, 1e-9)
    }

    @Test
    fun `default is empty tokens and cost`() {
        val metrics = RequestMetrics()
        assertEquals(TokenDetails(), metrics.tokens)
        assertEquals(CostDetails(), metrics.cost)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :session-storage:test --tests "com.ai.challenge.session.*Test" -v`
Expected: FAIL — classes not found.

- [ ] **Step 3: Create TurnId value class**

Create `session-storage/src/main/kotlin/com/ai/challenge/session/TurnId.kt`:

```kotlin
package com.ai.challenge.session

import java.util.UUID

@JvmInline
value class TurnId(val value: String) {
    companion object {
        fun generate(): TurnId = TurnId(UUID.randomUUID().toString())
    }
}
```

- [ ] **Step 4: Create TokenDetails**

Create `session-storage/src/main/kotlin/com/ai/challenge/session/TokenDetails.kt`:

```kotlin
package com.ai.challenge.session

data class TokenDetails(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val cachedTokens: Int = 0,
    val cacheWriteTokens: Int = 0,
    val reasoningTokens: Int = 0,
) {
    val totalTokens: Int get() = promptTokens + completionTokens

    operator fun plus(other: TokenDetails) = TokenDetails(
        promptTokens = promptTokens + other.promptTokens,
        completionTokens = completionTokens + other.completionTokens,
        cachedTokens = cachedTokens + other.cachedTokens,
        cacheWriteTokens = cacheWriteTokens + other.cacheWriteTokens,
        reasoningTokens = reasoningTokens + other.reasoningTokens,
    )
}
```

- [ ] **Step 5: Create CostDetails**

Create `session-storage/src/main/kotlin/com/ai/challenge/session/CostDetails.kt`:

```kotlin
package com.ai.challenge.session

data class CostDetails(
    val totalCost: Double = 0.0,
    val upstreamCost: Double = 0.0,
    val upstreamPromptCost: Double = 0.0,
    val upstreamCompletionsCost: Double = 0.0,
) {
    operator fun plus(other: CostDetails) = CostDetails(
        totalCost = totalCost + other.totalCost,
        upstreamCost = upstreamCost + other.upstreamCost,
        upstreamPromptCost = upstreamPromptCost + other.upstreamPromptCost,
        upstreamCompletionsCost = upstreamCompletionsCost + other.upstreamCompletionsCost,
    )
}
```

- [ ] **Step 6: Create RequestMetrics**

Create `session-storage/src/main/kotlin/com/ai/challenge/session/RequestMetrics.kt`:

```kotlin
package com.ai.challenge.session

data class RequestMetrics(
    val tokens: TokenDetails = TokenDetails(),
    val cost: CostDetails = CostDetails(),
) {
    operator fun plus(other: RequestMetrics) = RequestMetrics(
        tokens = tokens + other.tokens,
        cost = cost + other.cost,
    )
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :session-storage:test --tests "com.ai.challenge.session.*Test" -v`
Expected: ALL PASS.

- [ ] **Step 8: Commit**

```bash
git add session-storage/src/main/kotlin/com/ai/challenge/session/TurnId.kt \
  session-storage/src/main/kotlin/com/ai/challenge/session/TokenDetails.kt \
  session-storage/src/main/kotlin/com/ai/challenge/session/CostDetails.kt \
  session-storage/src/main/kotlin/com/ai/challenge/session/RequestMetrics.kt \
  session-storage/src/test/kotlin/com/ai/challenge/session/RequestMetricsTest.kt
git commit -m "feat: add TurnId, TokenDetails, CostDetails, RequestMetrics value objects"
```

---

## Task 2: UsageManager Interface + InMemoryUsageManager (session-storage)

**Files:**
- Create: `session-storage/src/main/kotlin/com/ai/challenge/session/UsageManager.kt`
- Create: `session-storage/src/main/kotlin/com/ai/challenge/session/InMemoryUsageManager.kt`

- [ ] **Step 1: Create UsageManager interface**

Create `session-storage/src/main/kotlin/com/ai/challenge/session/UsageManager.kt`:

```kotlin
package com.ai.challenge.session

interface UsageManager {
    fun record(turnId: TurnId, metrics: RequestMetrics)
    fun getByTurn(turnId: TurnId): RequestMetrics?
    fun getBySession(sessionId: SessionId): Map<TurnId, RequestMetrics>
    fun getSessionTotal(sessionId: SessionId): RequestMetrics
}
```

Note: synchronous like `AgentSessionManager` — Exposed transactions are blocking.

- [ ] **Step 2: Create InMemoryUsageManager**

Create `session-storage/src/main/kotlin/com/ai/challenge/session/InMemoryUsageManager.kt`:

```kotlin
package com.ai.challenge.session

import java.util.concurrent.ConcurrentHashMap

class InMemoryUsageManager(
    private val sessionManager: AgentSessionManager,
) : UsageManager {

    private val metrics = ConcurrentHashMap<TurnId, RequestMetrics>()

    override fun record(turnId: TurnId, metrics: RequestMetrics) {
        this.metrics[turnId] = metrics
    }

    override fun getByTurn(turnId: TurnId): RequestMetrics? = metrics[turnId]

    override fun getBySession(sessionId: SessionId): Map<TurnId, RequestMetrics> {
        val turnIds = sessionManager.getHistory(sessionId)
            .map { it.id }
            .toSet()
        return metrics.filterKeys { it in turnIds }
    }

    override fun getSessionTotal(sessionId: SessionId): RequestMetrics =
        getBySession(sessionId).values.fold(RequestMetrics()) { acc, m -> acc + m }
}
```

- [ ] **Step 3: Run session-storage compilation check**

Run: `./gradlew :session-storage:compileKotlin`
Expected: FAIL — `Turn` doesn't have `id` field yet. This is expected; we'll fix Turn in the next task.

- [ ] **Step 4: Commit (WIP)**

```bash
git add session-storage/src/main/kotlin/com/ai/challenge/session/UsageManager.kt \
  session-storage/src/main/kotlin/com/ai/challenge/session/InMemoryUsageManager.kt
git commit -m "feat: add UsageManager interface and InMemoryUsageManager"
```

---

## Task 3: Refactor Turn and SessionManager (session-storage)

**Files:**
- Modify: `session-storage/src/main/kotlin/com/ai/challenge/session/Turn.kt`
- Modify: `session-storage/src/main/kotlin/com/ai/challenge/session/AgentSessionManager.kt`
- Modify: `session-storage/src/main/kotlin/com/ai/challenge/session/InMemorySessionManager.kt`
- Modify: `session-storage/src/main/kotlin/com/ai/challenge/session/ExposedSessionManager.kt`
- Delete: `session-storage/src/main/kotlin/com/ai/challenge/session/TokenUsage.kt`

- [ ] **Step 1: Modify Turn — add TurnId, remove tokenUsage**

Replace `session-storage/src/main/kotlin/com/ai/challenge/session/Turn.kt` with:

```kotlin
package com.ai.challenge.session

import kotlin.time.Clock
import kotlin.time.Instant

data class Turn(
    val id: TurnId = TurnId.generate(),
    val userMessage: String,
    val agentResponse: String,
    val timestamp: Instant = Clock.System.now(),
)
```

- [ ] **Step 2: Modify AgentSessionManager — appendTurn returns TurnId**

Replace `session-storage/src/main/kotlin/com/ai/challenge/session/AgentSessionManager.kt` with:

```kotlin
package com.ai.challenge.session

interface AgentSessionManager {
    fun createSession(title: String = ""): SessionId
    fun getSession(id: SessionId): AgentSession?
    fun deleteSession(id: SessionId): Boolean
    fun listSessions(): List<AgentSession>
    fun getHistory(id: SessionId, limit: Int? = null): List<Turn>
    fun appendTurn(id: SessionId, turn: Turn): TurnId
    fun updateTitle(id: SessionId, title: String)
}
```

- [ ] **Step 3: Modify InMemorySessionManager**

Replace `session-storage/src/main/kotlin/com/ai/challenge/session/InMemorySessionManager.kt` with:

```kotlin
package com.ai.challenge.session

import java.util.concurrent.ConcurrentHashMap

class InMemorySessionManager : AgentSessionManager {

    private val sessions = ConcurrentHashMap<SessionId, AgentSession>()

    override fun createSession(title: String): SessionId {
        val session = AgentSession(id = SessionId.generate(), title = title)
        sessions[session.id] = session
        return session.id
    }

    override fun getSession(id: SessionId): AgentSession? = sessions[id]

    override fun deleteSession(id: SessionId): Boolean =
        sessions.remove(id) != null

    override fun listSessions(): List<AgentSession> =
        sessions.values.sortedByDescending { it.updatedAt }

    override fun getHistory(id: SessionId, limit: Int?): List<Turn> {
        val history = sessions[id]?.history ?: return emptyList()
        return if (limit != null) history.takeLast(limit) else history
    }

    override fun appendTurn(id: SessionId, turn: Turn): TurnId {
        sessions.computeIfPresent(id) { _, session -> session.addTurn(turn) }
        return turn.id
    }

    override fun updateTitle(id: SessionId, title: String) {
        sessions.computeIfPresent(id) { _, session ->
            session.copy(title = title, updatedAt = kotlin.time.Clock.System.now())
        }
    }
}
```

- [ ] **Step 4: Modify ExposedSessionManager — add `id` column, remove token columns, return TurnId**

Replace the full content of `session-storage/src/main/kotlin/com/ai/challenge/session/ExposedSessionManager.kt` with:

```kotlin
package com.ai.challenge.session

import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object SessionsTable : Table("sessions") {
    val id = varchar("id", 36)
    val title = varchar("title", 255).default("")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object TurnsTable : Table("turns") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
        .references(SessionsTable.id, onDelete = ReferenceOption.CASCADE)
    val userMessage = text("user_message")
    val agentResponse = text("agent_response")
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)
}

class ExposedSessionManager(private val database: Database) : AgentSessionManager {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(SessionsTable, TurnsTable)
        }
    }

    override fun createSession(title: String): SessionId {
        val sessionId = SessionId.generate()
        val now = Clock.System.now()
        transaction(database) {
            SessionsTable.insert {
                it[id] = sessionId.value
                it[SessionsTable.title] = title
                it[createdAt] = now.toEpochMilliseconds()
                it[updatedAt] = now.toEpochMilliseconds()
            }
        }
        return sessionId
    }

    override fun getSession(id: SessionId): AgentSession? = transaction(database) {
        val row = SessionsTable.selectAll()
            .where { SessionsTable.id eq id.value }
            .singleOrNull() ?: return@transaction null

        val history = loadHistory(id)

        AgentSession(
            id = id,
            title = row[SessionsTable.title],
            createdAt = Instant.fromEpochMilliseconds(row[SessionsTable.createdAt]),
            updatedAt = Instant.fromEpochMilliseconds(row[SessionsTable.updatedAt]),
            history = history,
        )
    }

    override fun deleteSession(id: SessionId): Boolean = transaction(database) {
        SessionsTable.deleteWhere { SessionsTable.id eq id.value } > 0
    }

    override fun listSessions(): List<AgentSession> = transaction(database) {
        SessionsTable.selectAll()
            .orderBy(SessionsTable.updatedAt, SortOrder.DESC)
            .map { row ->
                AgentSession(
                    id = SessionId(row[SessionsTable.id]),
                    title = row[SessionsTable.title],
                    createdAt = Instant.fromEpochMilliseconds(row[SessionsTable.createdAt]),
                    updatedAt = Instant.fromEpochMilliseconds(row[SessionsTable.updatedAt]),
                    history = emptyList(),
                )
            }
    }

    override fun getHistory(id: SessionId, limit: Int?): List<Turn> = transaction(database) {
        loadHistory(id, limit)
    }

    override fun appendTurn(id: SessionId, turn: Turn): TurnId {
        val now = Clock.System.now()
        transaction(database) {
            TurnsTable.insert {
                it[TurnsTable.id] = turn.id.value
                it[sessionId] = id.value
                it[userMessage] = turn.userMessage
                it[agentResponse] = turn.agentResponse
                it[timestamp] = turn.timestamp.toEpochMilliseconds()
            }
            SessionsTable.update({ SessionsTable.id eq id.value }) {
                it[updatedAt] = now.toEpochMilliseconds()
            }
        }
        return turn.id
    }

    override fun updateTitle(id: SessionId, title: String) {
        val now = Clock.System.now()
        transaction(database) {
            SessionsTable.update({ SessionsTable.id eq id.value }) {
                it[SessionsTable.title] = title
                it[updatedAt] = now.toEpochMilliseconds()
            }
        }
    }

    private fun loadHistory(id: SessionId, limit: Int? = null): List<Turn> {
        val query = TurnsTable.selectAll()
            .where { TurnsTable.sessionId eq id.value }
            .orderBy(TurnsTable.timestamp, SortOrder.ASC)

        val rows = if (limit != null) {
            val allRows = query.toList()
            if (allRows.size > limit) allRows.takeLast(limit) else allRows
        } else {
            query.toList()
        }

        return rows.map { row ->
            Turn(
                id = TurnId(row[TurnsTable.id]),
                userMessage = row[TurnsTable.userMessage],
                agentResponse = row[TurnsTable.agentResponse],
                timestamp = Instant.fromEpochMilliseconds(row[TurnsTable.timestamp]),
            )
        }
    }
}
```

- [ ] **Step 5: Delete TokenUsage.kt**

Delete file: `session-storage/src/main/kotlin/com/ai/challenge/session/TokenUsage.kt`

- [ ] **Step 6: Run session-storage tests**

Run: `./gradlew :session-storage:test -v`
Expected: ALL PASS (value object tests + compilation).

- [ ] **Step 7: Commit**

```bash
git add session-storage/src/main/kotlin/com/ai/challenge/session/Turn.kt \
  session-storage/src/main/kotlin/com/ai/challenge/session/AgentSessionManager.kt \
  session-storage/src/main/kotlin/com/ai/challenge/session/InMemorySessionManager.kt \
  session-storage/src/main/kotlin/com/ai/challenge/session/ExposedSessionManager.kt
git rm session-storage/src/main/kotlin/com/ai/challenge/session/TokenUsage.kt
git commit -m "refactor: remove TokenUsage, add TurnId to Turn, appendTurn returns TurnId"
```

---

## Task 4: ExposedUsageManager (session-storage)

**Files:**
- Create: `session-storage/src/main/kotlin/com/ai/challenge/session/ExposedUsageManager.kt`
- Create: `session-storage/src/test/kotlin/com/ai/challenge/session/ExposedUsageManagerTest.kt`

- [ ] **Step 1: Write failing tests for ExposedUsageManager**

Create `session-storage/src/test/kotlin/com/ai/challenge/session/ExposedUsageManagerTest.kt`:

```kotlin
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
        database = Database.connect("jdbc:sqlite::memory:", driver = "org.sqlite.JDBC")
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :session-storage:test --tests "com.ai.challenge.session.ExposedUsageManagerTest" -v`
Expected: FAIL — `ExposedUsageManager` class not found.

- [ ] **Step 3: Create ExposedUsageManager**

Create `session-storage/src/main/kotlin/com/ai/challenge/session/ExposedUsageManager.kt`:

```kotlin
package com.ai.challenge.session

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object RequestMetricsTable : Table("request_metrics") {
    val turnId = varchar("turn_id", 36)
        .references(TurnsTable.id, onDelete = ReferenceOption.CASCADE)
    val promptTokens = integer("prompt_tokens")
    val completionTokens = integer("completion_tokens")
    val cachedTokens = integer("cached_tokens")
    val cacheWriteTokens = integer("cache_write_tokens")
    val reasoningTokens = integer("reasoning_tokens")
    val totalCost = double("total_cost")
    val upstreamCost = double("upstream_cost")
    val upstreamPromptCost = double("upstream_prompt_cost")
    val upstreamCompletionsCost = double("upstream_completions_cost")

    override val primaryKey = PrimaryKey(turnId)
}

class ExposedUsageManager(private val database: Database) : UsageManager {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(RequestMetricsTable)
        }
    }

    override fun record(turnId: TurnId, metrics: RequestMetrics) {
        transaction(database) {
            RequestMetricsTable.insert {
                it[RequestMetricsTable.turnId] = turnId.value
                it[promptTokens] = metrics.tokens.promptTokens
                it[completionTokens] = metrics.tokens.completionTokens
                it[cachedTokens] = metrics.tokens.cachedTokens
                it[cacheWriteTokens] = metrics.tokens.cacheWriteTokens
                it[reasoningTokens] = metrics.tokens.reasoningTokens
                it[totalCost] = metrics.cost.totalCost
                it[upstreamCost] = metrics.cost.upstreamCost
                it[upstreamPromptCost] = metrics.cost.upstreamPromptCost
                it[upstreamCompletionsCost] = metrics.cost.upstreamCompletionsCost
            }
        }
    }

    override fun getByTurn(turnId: TurnId): RequestMetrics? = transaction(database) {
        RequestMetricsTable.selectAll()
            .where { RequestMetricsTable.turnId eq turnId.value }
            .singleOrNull()
            ?.toRequestMetrics()
    }

    override fun getBySession(sessionId: SessionId): Map<TurnId, RequestMetrics> = transaction(database) {
        (RequestMetricsTable innerJoin TurnsTable)
            .selectAll()
            .where { TurnsTable.sessionId eq sessionId.value }
            .associate { row ->
                TurnId(row[RequestMetricsTable.turnId]) to row.toRequestMetrics()
            }
    }

    override fun getSessionTotal(sessionId: SessionId): RequestMetrics =
        getBySession(sessionId).values.fold(RequestMetrics()) { acc, m -> acc + m }

    private fun ResultRow.toRequestMetrics() = RequestMetrics(
        tokens = TokenDetails(
            promptTokens = this[RequestMetricsTable.promptTokens],
            completionTokens = this[RequestMetricsTable.completionTokens],
            cachedTokens = this[RequestMetricsTable.cachedTokens],
            cacheWriteTokens = this[RequestMetricsTable.cacheWriteTokens],
            reasoningTokens = this[RequestMetricsTable.reasoningTokens],
        ),
        cost = CostDetails(
            totalCost = this[RequestMetricsTable.totalCost],
            upstreamCost = this[RequestMetricsTable.upstreamCost],
            upstreamPromptCost = this[RequestMetricsTable.upstreamPromptCost],
            upstreamCompletionsCost = this[RequestMetricsTable.upstreamCompletionsCost],
        ),
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :session-storage:test --tests "com.ai.challenge.session.ExposedUsageManagerTest" -v`
Expected: ALL PASS.

- [ ] **Step 5: Commit**

```bash
git add session-storage/src/main/kotlin/com/ai/challenge/session/ExposedUsageManager.kt \
  session-storage/src/test/kotlin/com/ai/challenge/session/ExposedUsageManagerTest.kt
git commit -m "feat: add ExposedUsageManager with RequestMetricsTable"
```

---

## Task 5: Extend llm-service API Models

**Files:**
- Modify: `llm-service/src/main/kotlin/com/ai/challenge/llm/model/ChatResponse.kt`
- Create: `llm-service/src/test/kotlin/com/ai/challenge/llm/model/ChatResponseParsingTest.kt`

- [ ] **Step 1: Write failing parsing tests**

Create `llm-service/src/test/kotlin/com/ai/challenge/llm/model/ChatResponseParsingTest.kt`:

```kotlin
package com.ai.challenge.llm.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatResponseParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses full response with all usage details`() {
        val input = """
        {
          "id": "gen-123",
          "choices": [{"index": 0, "message": {"role": "assistant", "content": "Hi"}}],
          "model": "test-model",
          "usage": {
            "prompt_tokens": 100,
            "completion_tokens": 50,
            "total_tokens": 150,
            "prompt_tokens_details": {
              "cached_tokens": 20,
              "cache_write_tokens": 80
            },
            "completion_tokens_details": {
              "reasoning_tokens": 10
            },
            "cost": 0.0015
          },
          "cost": 0.0015,
          "cost_details": {
            "upstream_inference_cost": 0.0012,
            "upstream_inference_prompt_cost": 0.0008,
            "upstream_inference_completions_cost": 0.0004
          }
        }
        """.trimIndent()

        val response = json.decodeFromString<ChatResponse>(input)

        val usage = response.usage!!
        assertEquals(100, usage.promptTokens)
        assertEquals(50, usage.completionTokens)
        assertEquals(150, usage.totalTokens)
        assertEquals(20, usage.promptTokensDetails!!.cachedTokens)
        assertEquals(80, usage.promptTokensDetails!!.cacheWriteTokens)
        assertEquals(10, usage.completionTokensDetails!!.reasoningTokens)
        assertEquals(0.0015, usage.cost!!, 1e-9)
        assertEquals(0.0015, response.cost!!, 1e-9)
        assertEquals(0.0012, response.costDetails!!.upstreamCost, 1e-9)
        assertEquals(0.0008, response.costDetails!!.upstreamPromptCost, 1e-9)
        assertEquals(0.0004, response.costDetails!!.upstreamCompletionsCost, 1e-9)
    }

    @Test
    fun `parses response without optional details`() {
        val input = """
        {
          "choices": [{"index": 0, "message": {"role": "assistant", "content": "Hi"}}],
          "usage": {
            "prompt_tokens": 100,
            "completion_tokens": 50,
            "total_tokens": 150
          }
        }
        """.trimIndent()

        val response = json.decodeFromString<ChatResponse>(input)

        val usage = response.usage!!
        assertEquals(100, usage.promptTokens)
        assertNull(usage.promptTokensDetails)
        assertNull(usage.completionTokensDetails)
        assertNull(usage.cost)
        assertNull(response.cost)
        assertNull(response.costDetails)
    }

    @Test
    fun `parses response without usage`() {
        val input = """
        {
          "choices": [{"index": 0, "message": {"role": "assistant", "content": "Hi"}}]
        }
        """.trimIndent()

        val response = json.decodeFromString<ChatResponse>(input)
        assertNull(response.usage)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :llm-service:test --tests "com.ai.challenge.llm.model.ChatResponseParsingTest" -v`
Expected: FAIL — fields not found on `ChatResponse`/`Usage`.

- [ ] **Step 3: Extend ChatResponse.kt with new models**

Replace `llm-service/src/main/kotlin/com/ai/challenge/llm/model/ChatResponse.kt` with:

```kotlin
package com.ai.challenge.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
    val model: String? = null,
    val usage: Usage? = null,
    val error: ErrorBody? = null,
    val cost: Double? = null,
    @SerialName("cost_details") val costDetails: ResponseCostDetails? = null,
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: Message,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    @SerialName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null,
    @SerialName("completion_tokens_details") val completionTokensDetails: CompletionTokensDetails? = null,
    val cost: Double? = null,
)

@Serializable
data class PromptTokensDetails(
    @SerialName("cached_tokens") val cachedTokens: Int = 0,
    @SerialName("cache_write_tokens") val cacheWriteTokens: Int = 0,
)

@Serializable
data class CompletionTokensDetails(
    @SerialName("reasoning_tokens") val reasoningTokens: Int = 0,
)

@Serializable
data class ResponseCostDetails(
    @SerialName("upstream_inference_cost") val upstreamCost: Double = 0.0,
    @SerialName("upstream_inference_prompt_cost") val upstreamPromptCost: Double = 0.0,
    @SerialName("upstream_inference_completions_cost") val upstreamCompletionsCost: Double = 0.0,
)

@Serializable
data class ErrorBody(val message: String? = null, val code: Int? = null)
```

Note: Named `ResponseCostDetails` (not `CostDetails`) to avoid confusion with the domain `CostDetails` in session-storage.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :llm-service:test --tests "com.ai.challenge.llm.model.ChatResponseParsingTest" -v`
Expected: ALL PASS.

- [ ] **Step 5: Commit**

```bash
git add llm-service/src/main/kotlin/com/ai/challenge/llm/model/ChatResponse.kt \
  llm-service/src/test/kotlin/com/ai/challenge/llm/model/ChatResponseParsingTest.kt
git commit -m "feat: extend ChatResponse with cached tokens, reasoning tokens, and cost fields"
```

---

## Task 6: Refactor Agent Layer

**Files:**
- Modify: `ai-agent/src/main/kotlin/com/ai/challenge/agent/AgentResponse.kt`
- Modify: `ai-agent/src/main/kotlin/com/ai/challenge/agent/OpenRouterAgent.kt`
- Modify: `ai-agent/src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt`

- [ ] **Step 1: Update AgentResponse**

Replace `ai-agent/src/main/kotlin/com/ai/challenge/agent/AgentResponse.kt` with:

```kotlin
package com.ai.challenge.agent

import com.ai.challenge.session.RequestMetrics
import com.ai.challenge.session.TurnId

data class AgentResponse(
    val text: String,
    val turnId: TurnId,
    val metrics: RequestMetrics,
)
```

- [ ] **Step 2: Update OpenRouterAgent**

Replace `ai-agent/src/main/kotlin/com/ai/challenge/agent/OpenRouterAgent.kt` with:

```kotlin
package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.llm.model.ChatResponse
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.CostDetails
import com.ai.challenge.session.RequestMetrics
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.TokenDetails
import com.ai.challenge.session.Turn
import com.ai.challenge.session.UsageManager

class OpenRouterAgent(
    private val service: OpenRouterService,
    private val model: String,
    private val sessionManager: AgentSessionManager,
    private val usageManager: UsageManager,
) : Agent {

    override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> = either {
        val history = sessionManager.getHistory(sessionId)

        val chatResponse = catch({
            service.chat(model = model) {
                for (turn in history) {
                    user(turn.userMessage)
                    assistant(turn.agentResponse)
                }
                user(message)
            }
        }) { e: Exception ->
            val msg = e.message ?: "Unknown error"
            if (msg.startsWith("OpenRouter API error:")) {
                raise(AgentError.ApiError(msg.removePrefix("OpenRouter API error: ")))
            } else {
                raise(AgentError.NetworkError(msg))
            }
        }

        val error = chatResponse.error
        if (error != null) {
            raise(AgentError.ApiError(error.message ?: "Unknown API error"))
        }

        val text = chatResponse.choices.firstOrNull()?.message?.content
            ?: raise(AgentError.ApiError("Empty response from OpenRouter"))

        val metrics = chatResponse.toRequestMetrics()
        val turn = Turn(userMessage = message, agentResponse = text)
        val turnId = sessionManager.appendTurn(sessionId, turn)
        usageManager.record(turnId, metrics)

        AgentResponse(text = text, turnId = turnId, metrics = metrics)
    }
}

private fun ChatResponse.toRequestMetrics(): RequestMetrics = RequestMetrics(
    tokens = TokenDetails(
        promptTokens = usage?.promptTokens ?: 0,
        completionTokens = usage?.completionTokens ?: 0,
        cachedTokens = usage?.promptTokensDetails?.cachedTokens ?: 0,
        cacheWriteTokens = usage?.promptTokensDetails?.cacheWriteTokens ?: 0,
        reasoningTokens = usage?.completionTokensDetails?.reasoningTokens ?: 0,
    ),
    cost = CostDetails(
        totalCost = usage?.cost ?: cost ?: 0.0,
        upstreamCost = costDetails?.upstreamCost ?: 0.0,
        upstreamPromptCost = costDetails?.upstreamPromptCost ?: 0.0,
        upstreamCompletionsCost = costDetails?.upstreamCompletionsCost ?: 0.0,
    ),
)
```

- [ ] **Step 3: Rewrite OpenRouterAgentTest**

Replace `ai-agent/src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt` with:

```kotlin
package com.ai.challenge.agent

import arrow.core.Either
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.session.CostDetails
import com.ai.challenge.session.InMemorySessionManager
import com.ai.challenge.session.InMemoryUsageManager
import com.ai.challenge.session.RequestMetrics
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.TokenDetails
import com.ai.challenge.session.Turn
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenRouterAgentTest {

    private val sessionManager = InMemorySessionManager()
    private val usageManager = InMemoryUsageManager(sessionManager)

    private fun createMockClient(responseJson: String): HttpClient {
        val mockEngine = MockEngine { _ ->
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
            }
        }
    }

    private fun createService(responseJson: String): OpenRouterService =
        OpenRouterService(
            apiKey = "test-key",
            client = createMockClient(responseJson),
        )

    private fun createAgent(responseJson: String): OpenRouterAgent =
        OpenRouterAgent(
            service = createService(responseJson),
            model = "test-model",
            sessionManager = sessionManager,
            usageManager = usageManager,
        )

    @Test
    fun `send returns Right with response text on success`() = runTest {
        val agent = createAgent("""
            {"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}
        """.trimIndent())
        val sessionId = sessionManager.createSession()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<AgentResponse>>(result)
        assertEquals("Hello!", result.value.text)
    }

    @Test
    fun `send returns AgentResponse with full metrics`() = runTest {
        val agent = createAgent("""
            {
              "choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}],
              "usage":{
                "prompt_tokens":100,"completion_tokens":50,"total_tokens":150,
                "prompt_tokens_details":{"cached_tokens":20,"cache_write_tokens":80},
                "completion_tokens_details":{"reasoning_tokens":10},
                "cost":0.0015
              },
              "cost":0.0015,
              "cost_details":{
                "upstream_inference_cost":0.0012,
                "upstream_inference_prompt_cost":0.0008,
                "upstream_inference_completions_cost":0.0004
              }
            }
        """.trimIndent())
        val sessionId = sessionManager.createSession()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<AgentResponse>>(result)
        val metrics = result.value.metrics
        assertEquals(TokenDetails(promptTokens = 100, completionTokens = 50, cachedTokens = 20, cacheWriteTokens = 80, reasoningTokens = 10), metrics.tokens)
        assertEquals(0.0015, metrics.cost.totalCost, 1e-9)
        assertEquals(0.0012, metrics.cost.upstreamCost, 1e-9)
        assertEquals(0.0008, metrics.cost.upstreamPromptCost, 1e-9)
        assertEquals(0.0004, metrics.cost.upstreamCompletionsCost, 1e-9)
    }

    @Test
    fun `send returns default RequestMetrics when usage is null`() = runTest {
        val agent = createAgent("""
            {"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}
        """.trimIndent())
        val sessionId = sessionManager.createSession()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<AgentResponse>>(result)
        assertEquals(RequestMetrics(), result.value.metrics)
    }

    @Test
    fun `send persists metrics via usageManager`() = runTest {
        val agent = createAgent("""
            {
              "choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}],
              "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}
            }
        """.trimIndent())
        val sessionId = sessionManager.createSession()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<AgentResponse>>(result)
        val stored = usageManager.getByTurn(result.value.turnId)
        assertNotNull(stored)
        assertEquals(10, stored.tokens.promptTokens)
        assertEquals(5, stored.tokens.completionTokens)
    }

    @Test
    fun `send saves turn to session on success`() = runTest {
        val agent = createAgent("""
            {"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}
        """.trimIndent())
        val sessionId = sessionManager.createSession()

        agent.send(sessionId, "Hi")

        val history = sessionManager.getHistory(sessionId)
        assertEquals(1, history.size)
        assertEquals("Hi", history[0].userMessage)
        assertEquals("Hello!", history[0].agentResponse)
    }

    @Test
    fun `send does not save turn on failure`() = runTest {
        val agent = createAgent("""
            {"error":{"message":"Rate limit exceeded","code":429},"choices":[]}
        """.trimIndent())
        val sessionId = sessionManager.createSession()

        agent.send(sessionId, "Hi")

        val history = sessionManager.getHistory(sessionId)
        assertTrue(history.isEmpty())
    }

    @Test
    fun `send returns Left ApiError when response has error`() = runTest {
        val agent = createAgent("""
            {"error":{"message":"Rate limit exceeded","code":429},"choices":[]}
        """.trimIndent())
        val sessionId = sessionManager.createSession()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Left<AgentError>>(result)
        assertIs<AgentError.ApiError>(result.value)
        assertEquals("Rate limit exceeded", result.value.message)
    }

    @Test
    fun `send returns Left NetworkError when service throws`() = runTest {
        val mockEngine = MockEngine { _ ->
            throw RuntimeException("Connection refused")
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
            }
        }
        val service = OpenRouterService(apiKey = "test-key", client = client)
        val agent = OpenRouterAgent(service, model = "test-model", sessionManager = sessionManager, usageManager = usageManager)
        val sessionId = sessionManager.createSession()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Left<AgentError>>(result)
        assertIs<AgentError.NetworkError>(result.value)
        assertEquals("Connection refused", result.value.message)
    }

    @Test
    fun `send includes history in LLM request`() = runTest {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"choices":[{"index":0,"message":{"role":"assistant","content":"I remember!"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
            }
        }
        val service = OpenRouterService(apiKey = "test-key", client = client)
        val agent = OpenRouterAgent(service, model = "test-model", sessionManager = sessionManager, usageManager = usageManager)
        val sessionId = sessionManager.createSession()

        sessionManager.appendTurn(sessionId, Turn(userMessage = "Hi", agentResponse = "Hello!"))

        agent.send(sessionId, "Remember me?")

        val json = Json.parseToJsonElement(capturedBody!!).jsonObject
        val messages = json["messages"]!!.jsonArray
        assertEquals(3, messages.size)
        assertEquals("user", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Hi", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("assistant", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Hello!", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[2].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Remember me?", messages[2].jsonObject["content"]!!.jsonPrimitive.content)
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :ai-agent:test -v`
Expected: ALL PASS.

- [ ] **Step 5: Commit**

```bash
git add ai-agent/src/main/kotlin/com/ai/challenge/agent/AgentResponse.kt \
  ai-agent/src/main/kotlin/com/ai/challenge/agent/OpenRouterAgent.kt \
  ai-agent/src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt
git commit -m "refactor: OpenRouterAgent uses RequestMetrics and UsageManager"
```

---

## Task 7: Refactor ChatStore (compose-ui)

**Files:**
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/model/UiMessage.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt`
- Modify: `compose-ui/src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt`

- [ ] **Step 1: Update UiMessage**

Replace `compose-ui/src/main/kotlin/com/ai/challenge/ui/model/UiMessage.kt` with:

```kotlin
package com.ai.challenge.ui.model

import com.ai.challenge.session.TurnId

data class UiMessage(
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val turnId: TurnId? = null,
)
```

- [ ] **Step 2: Update ChatStore interface**

Replace `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt` with:

```kotlin
package com.ai.challenge.ui.chat.store

import com.ai.challenge.session.RequestMetrics
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.TurnId
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store

interface ChatStore : Store<ChatStore.Intent, ChatStore.State, Nothing> {

    sealed interface Intent {
        data class SendMessage(val text: String) : Intent
        data class LoadSession(val sessionId: SessionId) : Intent
    }

    data class State(
        val sessionId: SessionId? = null,
        val messages: List<UiMessage> = emptyList(),
        val isLoading: Boolean = false,
        val turnMetrics: Map<TurnId, RequestMetrics> = emptyMap(),
        val sessionMetrics: RequestMetrics = RequestMetrics(),
    )
}
```

- [ ] **Step 3: Update ChatStoreFactory**

Replace `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt` with:

```kotlin
package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.agent.Agent
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.RequestMetrics
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.TurnId
import com.ai.challenge.session.UsageManager
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class ChatStoreFactory(
    private val storeFactory: StoreFactory,
    private val agent: Agent,
    private val sessionManager: AgentSessionManager,
    private val usageManager: UsageManager,
) {
    fun create(): ChatStore =
        object : ChatStore,
            Store<ChatStore.Intent, ChatStore.State, Nothing> by storeFactory.create(
                name = "ChatStore",
                initialState = ChatStore.State(),
                executorFactory = { ExecutorImpl(agent, sessionManager, usageManager) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class SessionLoaded(
            val sessionId: SessionId,
            val messages: List<UiMessage>,
            val turnMetrics: Map<TurnId, RequestMetrics>,
            val sessionMetrics: RequestMetrics,
        ) : Msg
        data class UserMessage(val text: String) : Msg
        data class AgentResponseMsg(
            val text: String,
            val turnId: TurnId,
            val metrics: RequestMetrics,
        ) : Msg
        data class Error(val text: String) : Msg
        data object Loading : Msg
        data object LoadingComplete : Msg
    }

    private class ExecutorImpl(
        private val agent: Agent,
        private val sessionManager: AgentSessionManager,
        private val usageManager: UsageManager,
    ) : CoroutineExecutor<ChatStore.Intent, Nothing, ChatStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: ChatStore.Intent) {
            when (intent) {
                is ChatStore.Intent.SendMessage -> handleSendMessage(intent.text)
                is ChatStore.Intent.LoadSession -> handleLoadSession(intent.sessionId)
            }
        }

        private fun handleLoadSession(sessionId: SessionId) {
            scope.launch {
                val history = sessionManager.getHistory(sessionId)
                val messages = history.flatMap { turn ->
                    listOf(
                        UiMessage(text = turn.userMessage, isUser = true, turnId = turn.id),
                        UiMessage(text = turn.agentResponse, isUser = false, turnId = turn.id),
                    )
                }
                val turnMetrics = usageManager.getBySession(sessionId)
                val sessionMetrics = turnMetrics.values.fold(RequestMetrics()) { acc, m -> acc + m }
                dispatch(Msg.SessionLoaded(sessionId, messages, turnMetrics, sessionMetrics))
            }
        }

        private fun handleSendMessage(text: String) {
            val sessionId = state().sessionId ?: return
            dispatch(Msg.UserMessage(text))
            dispatch(Msg.Loading)

            scope.launch {
                when (val result = agent.send(sessionId, text)) {
                    is Either.Right -> dispatch(
                        Msg.AgentResponseMsg(
                            text = result.value.text,
                            turnId = result.value.turnId,
                            metrics = result.value.metrics,
                        )
                    )
                    is Either.Left -> dispatch(Msg.Error(result.value.message))
                }
                dispatch(Msg.LoadingComplete)

                val session = sessionManager.getSession(sessionId)
                if (session != null && session.title.isEmpty()) {
                    val title = text.take(50)
                    sessionManager.updateTitle(sessionId, title)
                }
            }
        }
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<ChatStore.State, Msg> {
        override fun ChatStore.State.reduce(msg: Msg): ChatStore.State =
            when (msg) {
                is Msg.SessionLoaded -> copy(
                    sessionId = msg.sessionId,
                    messages = msg.messages,
                    turnMetrics = msg.turnMetrics,
                    sessionMetrics = msg.sessionMetrics,
                )
                is Msg.UserMessage -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = true),
                )
                is Msg.AgentResponseMsg -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false, turnId = msg.turnId),
                    turnMetrics = turnMetrics + (msg.turnId to msg.metrics),
                    sessionMetrics = sessionMetrics + msg.metrics,
                )
                is Msg.Error -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false, isError = true),
                )
                is Msg.Loading -> copy(isLoading = true)
                is Msg.LoadingComplete -> copy(isLoading = false)
            }
    }
}
```

- [ ] **Step 4: Rewrite ChatStoreTest**

Replace `compose-ui/src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt` with:

```kotlin
package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.agent.Agent
import com.ai.challenge.agent.AgentError
import com.ai.challenge.agent.AgentResponse
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.CostDetails
import com.ai.challenge.session.InMemorySessionManager
import com.ai.challenge.session.InMemoryUsageManager
import com.ai.challenge.session.RequestMetrics
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.TokenDetails
import com.ai.challenge.session.Turn
import com.ai.challenge.session.TurnId
import com.ai.challenge.session.UsageManager
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ChatStoreTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createStore(
        agent: Agent = FakeAgent(),
        sessionManager: AgentSessionManager = InMemorySessionManager(),
        usageManager: UsageManager = InMemoryUsageManager(sessionManager),
    ): ChatStore = ChatStoreFactory(DefaultStoreFactory(), agent, sessionManager, usageManager).create()

    @Test
    fun `initial state is empty with no session`() {
        val store = createStore()

        assertEquals(emptyList(), store.state.messages)
        assertFalse(store.state.isLoading)
        assertNull(store.state.sessionId)
        assertEquals(emptyMap(), store.state.turnMetrics)
        assertEquals(RequestMetrics(), store.state.sessionMetrics)

        store.dispose()
    }

    @Test
    fun `LoadSession sets sessionId and loads history as UiMessages`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        sessionManager.appendTurn(sessionId, Turn(userMessage = "hi", agentResponse = "hello"))
        val store = createStore(sessionManager = sessionManager)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        assertEquals(sessionId, store.state.sessionId)
        assertEquals(2, store.state.messages.size)
        assertEquals("hi", store.state.messages[0].text)
        assertEquals(true, store.state.messages[0].isUser)
        assertEquals("hello", store.state.messages[1].text)
        assertEquals(false, store.state.messages[1].isUser)

        store.dispose()
    }

    @Test
    fun `SendMessage adds user message and agent response`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val turnId = TurnId.generate()
        val agent = FakeAgent(response = Either.Right(AgentResponse("Hello from agent!", turnId, RequestMetrics())))
        val store = createStore(agent = agent, sessionManager = sessionManager)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hi"))
        advanceUntilIdle()

        val messages = store.state.messages
        assertEquals(2, messages.size)
        assertEquals("Hi", messages[0].text)
        assertEquals(true, messages[0].isUser)
        assertEquals("Hello from agent!", messages[1].text)
        assertEquals(false, messages[1].isUser)
        assertFalse(store.state.isLoading)

        store.dispose()
    }

    @Test
    fun `SendMessage adds error message on agent failure`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val agent = FakeAgent(response = Either.Left(AgentError.NetworkError("Timeout")))
        val store = createStore(agent = agent, sessionManager = sessionManager)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hi"))
        advanceUntilIdle()

        val messages = store.state.messages
        assertEquals(2, messages.size)
        assertEquals(UiMessage("Hi", isUser = true), messages[0])
        assertEquals(UiMessage("Timeout", isUser = false, isError = true), messages[1])
        assertFalse(store.state.isLoading)

        store.dispose()
    }

    @Test
    fun `SendMessage populates turnMetrics and sessionMetrics`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val turnId = TurnId.generate()
        val metrics = RequestMetrics(
            tokens = TokenDetails(promptTokens = 100, completionTokens = 50),
            cost = CostDetails(totalCost = 0.001),
        )
        val agent = FakeAgent(response = Either.Right(AgentResponse(text = "Hi!", turnId = turnId, metrics = metrics)))
        val store = createStore(agent = agent, sessionManager = sessionManager)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hello"))
        advanceUntilIdle()

        assertEquals(metrics, store.state.turnMetrics[turnId])
        assertEquals(metrics, store.state.sessionMetrics)

        store.dispose()
    }

    @Test
    fun `SendMessage accumulates sessionMetrics across multiple turns`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        val metrics1 = RequestMetrics(tokens = TokenDetails(promptTokens = 100, completionTokens = 50), cost = CostDetails(totalCost = 0.001))
        val metrics2 = RequestMetrics(tokens = TokenDetails(promptTokens = 200, completionTokens = 100), cost = CostDetails(totalCost = 0.002))

        var callCount = 0
        val agent = object : Agent {
            override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> {
                callCount++
                return if (callCount == 1) {
                    Either.Right(AgentResponse("r1", turnId1, metrics1))
                } else {
                    Either.Right(AgentResponse("r2", turnId2, metrics2))
                }
            }
        }
        val store = createStore(agent = agent, sessionManager = sessionManager)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hello"))
        advanceUntilIdle()
        store.accept(ChatStore.Intent.SendMessage("Again"))
        advanceUntilIdle()

        assertEquals(metrics1 + metrics2, store.state.sessionMetrics)
        assertEquals(2, store.state.turnMetrics.size)

        store.dispose()
    }

    @Test
    fun `LoadSession loads turnMetrics from usageManager`() = runTest {
        val sessionManager = InMemorySessionManager()
        val usageManager = InMemoryUsageManager(sessionManager)
        val sessionId = sessionManager.createSession()

        val turn1 = Turn(userMessage = "a", agentResponse = "b")
        val turn2 = Turn(userMessage = "c", agentResponse = "d")
        val turnId1 = sessionManager.appendTurn(sessionId, turn1)
        val turnId2 = sessionManager.appendTurn(sessionId, turn2)
        val m1 = RequestMetrics(tokens = TokenDetails(promptTokens = 10, completionTokens = 5), cost = CostDetails(totalCost = 0.001))
        val m2 = RequestMetrics(tokens = TokenDetails(promptTokens = 20, completionTokens = 10), cost = CostDetails(totalCost = 0.002))
        usageManager.record(turnId1, m1)
        usageManager.record(turnId2, m2)

        val store = createStore(sessionManager = sessionManager, usageManager = usageManager)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        assertEquals(m1, store.state.turnMetrics[turnId1])
        assertEquals(m2, store.state.turnMetrics[turnId2])
        assertEquals(m1 + m2, store.state.sessionMetrics)

        store.dispose()
    }

    @Test
    fun `SendMessage auto-titles session on first message`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val agent = FakeAgent(response = Either.Right(AgentResponse("response", TurnId.generate(), RequestMetrics())))
        val store = createStore(agent = agent, sessionManager = sessionManager)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hello world, this is a long message for auto-title testing purposes"))
        advanceUntilIdle()

        val title = sessionManager.getSession(sessionId)?.title ?: ""
        assertEquals("Hello world, this is a long message for auto-title", title)

        store.dispose()
    }
}

class FakeAgent(
    private val response: Either<AgentError, AgentResponse> = Either.Right(AgentResponse("", TurnId.generate(), RequestMetrics())),
) : Agent {
    override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> = response
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :compose-ui:test -v`
Expected: ALL PASS.

- [ ] **Step 6: Commit**

```bash
git add compose-ui/src/main/kotlin/com/ai/challenge/ui/model/UiMessage.kt \
  compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt \
  compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt \
  compose-ui/src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt
git commit -m "refactor: ChatStore uses RequestMetrics and turnMetrics map"
```

---

## Task 8: Update UI Components and DI (compose-ui)

**Files:**
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatComponent.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/di/AppModule.kt`

- [ ] **Step 1: Update ChatContent — per-message metrics and session bar**

Replace `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt` with:

```kotlin
package com.ai.challenge.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.ai.challenge.session.RequestMetrics
import com.ai.challenge.ui.model.UiMessage

@Composable
fun ChatContent(component: ChatComponent) {
    val state by component.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages) { message ->
                    val metrics = message.turnId?.let { state.turnMetrics[it] }
                    MessageBubble(message, metrics)
                }
                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && inputText.isNotBlank() && !state.isLoading) {
                                component.onSendMessage(inputText.trim())
                                inputText = ""
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = { Text("Type a message...") },
                    enabled = !state.isLoading,
                    singleLine = true,
                )
                TextButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            component.onSendMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !state.isLoading,
                ) {
                    Text("Send")
                }
            }
            if (state.sessionMetrics.tokens.totalTokens > 0) {
                SessionMetricsBar(state.sessionMetrics)
            }
    }
}

@Composable
private fun MessageBubble(message: UiMessage, metrics: RequestMetrics?) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        message.isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        message.isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 600.dp),
        ) {
            Text(
                text = message.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor)
                    .padding(12.dp),
                color = textColor,
            )
            if (!message.isUser && metrics != null && metrics.tokens.totalTokens > 0) {
                Text(
                    text = formatTurnMetrics(metrics),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SessionMetricsBar(metrics: RequestMetrics) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = formatSessionMetrics(metrics),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTurnMetrics(metrics: RequestMetrics): String {
    val parts = mutableListOf<String>()
    parts.add("\u2191${metrics.tokens.promptTokens}")
    parts.add("\u2193${metrics.tokens.completionTokens}")
    if (metrics.tokens.cachedTokens > 0) parts.add("cached:${metrics.tokens.cachedTokens}")
    if (metrics.tokens.reasoningTokens > 0) parts.add("reasoning:${metrics.tokens.reasoningTokens}")
    if (metrics.cost.totalCost > 0) parts.add("$${String.format("%.4f", metrics.cost.totalCost)}")
    return parts.joinToString("  ")
}

private fun formatSessionMetrics(metrics: RequestMetrics): String {
    val parts = mutableListOf<String>()
    parts.add("Session: \u2191${metrics.tokens.promptTokens}  \u2193${metrics.tokens.completionTokens}")
    if (metrics.tokens.cachedTokens > 0) parts.add("cached:${metrics.tokens.cachedTokens}")
    if (metrics.cost.totalCost > 0) parts.add("Total: $${String.format("%.4f", metrics.cost.totalCost)}")
    if (metrics.cost.upstreamCost > 0) parts.add("Upstream: $${String.format("%.4f", metrics.cost.upstreamCost)}")
    return parts.joinToString("  |  ")
}
```

- [ ] **Step 2: Update ChatComponent — pass usageManager**

Replace `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatComponent.kt` with:

```kotlin
package com.ai.challenge.ui.chat

import com.ai.challenge.agent.Agent
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.UsageManager
import com.ai.challenge.ui.chat.store.ChatStore
import com.ai.challenge.ui.chat.store.ChatStoreFactory
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow

class ChatComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    agent: Agent,
    sessionManager: AgentSessionManager,
    usageManager: UsageManager,
    sessionId: SessionId,
) : ComponentContext by componentContext {

    private val store = instanceKeeper.getStore {
        ChatStoreFactory(storeFactory, agent, sessionManager, usageManager).create()
    }

    init {
        store.accept(ChatStore.Intent.LoadSession(sessionId))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ChatStore.State> = store.stateFlow

    fun onSendMessage(text: String) {
        store.accept(ChatStore.Intent.SendMessage(text))
    }
}
```

- [ ] **Step 3: Update AppModule — register UsageManager**

Replace `compose-ui/src/main/kotlin/com/ai/challenge/ui/di/AppModule.kt` with:

```kotlin
package com.ai.challenge.ui.di

import com.ai.challenge.agent.Agent
import com.ai.challenge.agent.OpenRouterAgent
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.ExposedSessionManager
import com.ai.challenge.session.ExposedUsageManager
import com.ai.challenge.session.UsageManager
import com.ai.challenge.session.createSessionDatabase
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module

val appModule = module {
    single {
        OpenRouterService(
            apiKey = System.getenv("OPENROUTER_API_KEY")
                ?: error("OPENROUTER_API_KEY environment variable is not set"),
        )
    }
    single<Database> { createSessionDatabase() }
    single<AgentSessionManager> { ExposedSessionManager(get()) }
    single<UsageManager> { ExposedUsageManager(get()) }
    single<Agent> {
        OpenRouterAgent(
            service = get(),
            model = "google/gemini-2.0-flash-001",
            sessionManager = get(),
            usageManager = get(),
        )
    }
}
```

- [ ] **Step 4: Fix any remaining ChatComponent callers**

Search for all places that instantiate `ChatComponent` and add the `usageManager` parameter. The caller is likely in a Decompose root component. Find and update it.

Run: `grep -r "ChatComponent(" compose-ui/src/main/kotlin/` to locate callers.

Add `usageManager = get()` (or however the DI provides it) to each call site.

- [ ] **Step 5: Run full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — all modules compile, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt \
  compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatComponent.kt \
  compose-ui/src/main/kotlin/com/ai/challenge/ui/di/AppModule.kt
git add -u  # catch any remaining caller updates
git commit -m "feat: update UI to display full request metrics with cached tokens and cost"
```

---

## Task 9: Database Migration

**Files:**
- Existing SQLite database at `~/.ai-challenge/sessions.db`

- [ ] **Step 1: Delete the old dev database**

Since this is dev-only data, the simplest approach is to delete the old database. `SchemaUtils.createMissingTablesAndColumns` will create the new schema on next run.

```bash
rm -f ~/.ai-challenge/sessions.db
```

Note: If preserving data matters, instead write a migration script. But for dev data, a fresh start is cleanest.

- [ ] **Step 2: Run the application to verify schema creation**

Run: `OPENROUTER_API_KEY=<key> ./gradlew :compose-ui:run`
Expected: Application starts, creates new database with updated schema (turns with varchar id, request_metrics table).

- [ ] **Step 3: Send a test message and verify metrics display**

Send a message in the chat. Verify:
- Assistant bubble shows token counts (prompt, completion) and cost
- If the model returns cached tokens, they appear
- Session bar at bottom shows accumulated totals
- No crashes, no missing data

- [ ] **Step 4: Commit (no code changes — just verification)**

No commit needed — this was a manual verification step.

---

## Task 10: Final Verification

- [ ] **Step 1: Run all tests across all modules**

Run: `./gradlew test -v`
Expected: ALL PASS across session-storage, llm-service, ai-agent, compose-ui.

- [ ] **Step 2: Run full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verify no references to old TokenUsage remain**

Run: `grep -r "TokenUsage" --include="*.kt" .`
Expected: No results (TokenUsage fully removed).

- [ ] **Step 4: Final commit if any cleanup was needed**

If any fixes were required, commit them:

```bash
git add -u
git commit -m "chore: final cleanup after request metrics extraction"
```
