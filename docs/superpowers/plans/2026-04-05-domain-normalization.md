# Domain Normalization & Repository Extraction — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Normalize domain models, extract repositories into separate modules, and centralize data access through the Agent facade.

**Architecture:** Extract a `core` module with domain models and repository interfaces. Create 4 independent repository implementation modules (session, turn, token, cost) each with its own Exposed/SQLite implementation and DatabaseFactory. Extend Agent as a facade so UI only depends on Agent. Delete `session-storage`.

**Tech Stack:** Kotlin 2.3.20, Exposed 0.61.0, SQLite JDBC 3.49.1.0, Arrow 2.1.2, Kotlinx DateTime 0.7.1

---

### Task 1: Create feature branch

**Files:**
- None

- [ ] **Step 1: Create and switch to feature branch**

```bash
git checkout -b feature/domain-normalization
```

- [ ] **Step 2: Verify branch**

Run: `git branch --show-current`
Expected: `feature/domain-normalization`

---

### Task 2: Create `core` module — models and ID types

**Files:**
- Create: `core/build.gradle.kts`
- Create: `core/src/main/kotlin/com/ai/challenge/core/SessionId.kt`
- Create: `core/src/main/kotlin/com/ai/challenge/core/TurnId.kt`
- Create: `core/src/main/kotlin/com/ai/challenge/core/AgentSession.kt`
- Create: `core/src/main/kotlin/com/ai/challenge/core/Turn.kt`
- Create: `core/src/main/kotlin/com/ai/challenge/core/TokenDetails.kt`
- Create: `core/src/main/kotlin/com/ai/challenge/core/CostDetails.kt`
- Create: `core/src/test/kotlin/com/ai/challenge/core/TokenDetailsTest.kt`
- Create: `core/src/test/kotlin/com/ai/challenge/core/CostDetailsTest.kt`
- Create: `core/src/test/kotlin/com/ai/challenge/core/TurnIdTest.kt`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create `core/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.ai.challenge"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.arrow.core)
    implementation(libs.kotlinx.datetime)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 2: Add `core` to `settings.gradle.kts`**

Add `include("core")` after `include("llm-service")`:

```kotlin
include("llm-service")
include("core")
include("ai-agent")
include("session-storage")
include("compose-ui")
include("week1")
```

- [ ] **Step 3: Create `SessionId.kt`**

```kotlin
package com.ai.challenge.core

import java.util.UUID

@JvmInline
value class SessionId(val value: String) {
    companion object {
        fun generate(): SessionId = SessionId(UUID.randomUUID().toString())
    }
}
```

- [ ] **Step 4: Create `TurnId.kt`**

```kotlin
package com.ai.challenge.core

import java.util.UUID

@JvmInline
value class TurnId(val value: String) {
    companion object {
        fun generate(): TurnId = TurnId(UUID.randomUUID().toString())
    }
}
```

- [ ] **Step 5: Create `AgentSession.kt`** (without `history` field)

```kotlin
package com.ai.challenge.core

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class AgentSession(
    val id: SessionId,
    val title: String = "",
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
)
```

Note: uses `kotlinx.datetime` instead of `kotlin.time` for stability. If the existing code uses `kotlin.time.Clock` and `kotlin.time.Instant`, match that instead.

- [ ] **Step 6: Create `Turn.kt`**

```kotlin
package com.ai.challenge.core

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class Turn(
    val id: TurnId = TurnId.generate(),
    val userMessage: String,
    val agentResponse: String,
    val timestamp: Instant = Clock.System.now(),
)
```

Note: Same as above — match the existing `kotlin.time` vs `kotlinx.datetime` import pattern from the original code. The original uses `kotlin.time.Clock` and `kotlin.time.Instant`.

- [ ] **Step 7: Create `TokenDetails.kt`**

```kotlin
package com.ai.challenge.core

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

- [ ] **Step 8: Create `CostDetails.kt`**

```kotlin
package com.ai.challenge.core

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

- [ ] **Step 9: Create tests — `TurnIdTest.kt`**

```kotlin
package com.ai.challenge.core

import kotlin.test.Test

class TurnIdTest {
    @Test
    fun `generate creates unique TurnIds`() {
        val id1 = TurnId.generate()
        val id2 = TurnId.generate()
        assert(id1 != id2)
    }
}
```

- [ ] **Step 10: Create tests — `TokenDetailsTest.kt`**

```kotlin
package com.ai.challenge.core

import kotlin.test.Test
import kotlin.test.assertEquals

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
```

- [ ] **Step 11: Create tests — `CostDetailsTest.kt`**

```kotlin
package com.ai.challenge.core

import kotlin.test.Test
import kotlin.test.assertEquals

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
```

- [ ] **Step 12: Run tests to verify core module compiles and passes**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 13: Commit**

```bash
git add core/ settings.gradle.kts
git commit -m "feat: create core module with domain models and ID types"
```

---

### Task 3: Add repository interfaces and Agent interface to `core`

**Files:**
- Create: `core/src/main/kotlin/com/ai/challenge/core/SessionRepository.kt`
- Create: `core/src/main/kotlin/com/ai/challenge/core/TurnRepository.kt`
- Create: `core/src/main/kotlin/com/ai/challenge/core/TokenRepository.kt`
- Create: `core/src/main/kotlin/com/ai/challenge/core/CostRepository.kt`
- Create: `core/src/main/kotlin/com/ai/challenge/core/Agent.kt`
- Create: `core/src/main/kotlin/com/ai/challenge/core/AgentError.kt`
- Create: `core/src/main/kotlin/com/ai/challenge/core/AgentResponse.kt`

- [ ] **Step 1: Create `SessionRepository.kt`**

```kotlin
package com.ai.challenge.core

interface SessionRepository {
    suspend fun create(title: String = ""): SessionId
    suspend fun get(id: SessionId): AgentSession?
    suspend fun delete(id: SessionId): Boolean
    suspend fun list(): List<AgentSession>
    suspend fun updateTitle(id: SessionId, title: String)
}
```

- [ ] **Step 2: Create `TurnRepository.kt`**

```kotlin
package com.ai.challenge.core

interface TurnRepository {
    suspend fun append(sessionId: SessionId, turn: Turn): TurnId
    suspend fun getBySession(sessionId: SessionId, limit: Int? = null): List<Turn>
    suspend fun get(turnId: TurnId): Turn?
}
```

- [ ] **Step 3: Create `TokenRepository.kt`**

```kotlin
package com.ai.challenge.core

interface TokenRepository {
    suspend fun record(sessionId: SessionId, turnId: TurnId, details: TokenDetails)
    suspend fun getByTurn(turnId: TurnId): TokenDetails?
    suspend fun getBySession(sessionId: SessionId): Map<TurnId, TokenDetails>
    suspend fun getSessionTotal(sessionId: SessionId): TokenDetails
}
```

- [ ] **Step 4: Create `CostRepository.kt`**

```kotlin
package com.ai.challenge.core

interface CostRepository {
    suspend fun record(sessionId: SessionId, turnId: TurnId, details: CostDetails)
    suspend fun getByTurn(turnId: TurnId): CostDetails?
    suspend fun getBySession(sessionId: SessionId): Map<TurnId, CostDetails>
    suspend fun getSessionTotal(sessionId: SessionId): CostDetails
}
```

- [ ] **Step 5: Create `AgentError.kt`**

```kotlin
package com.ai.challenge.core

sealed interface AgentError {
    val message: String

    data class NetworkError(override val message: String) : AgentError
    data class ApiError(override val message: String) : AgentError
}
```

- [ ] **Step 6: Create `AgentResponse.kt`**

```kotlin
package com.ai.challenge.core

data class AgentResponse(
    val text: String,
    val turnId: TurnId,
    val tokenDetails: TokenDetails,
    val costDetails: CostDetails,
)
```

- [ ] **Step 7: Create `Agent.kt`**

```kotlin
package com.ai.challenge.core

import arrow.core.Either

interface Agent {
    suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse>
    suspend fun createSession(title: String = ""): SessionId
    suspend fun deleteSession(id: SessionId): Boolean
    suspend fun listSessions(): List<AgentSession>
    suspend fun getSession(id: SessionId): AgentSession?
    suspend fun getTurns(sessionId: SessionId, limit: Int? = null): List<Turn>
    suspend fun getTokensByTurn(turnId: TurnId): TokenDetails?
    suspend fun getTokensBySession(sessionId: SessionId): Map<TurnId, TokenDetails>
    suspend fun getSessionTotalTokens(sessionId: SessionId): TokenDetails
    suspend fun getCostByTurn(turnId: TurnId): CostDetails?
    suspend fun getCostBySession(sessionId: SessionId): Map<TurnId, CostDetails>
    suspend fun getSessionTotalCost(sessionId: SessionId): CostDetails
}
```

- [ ] **Step 8: Verify core module compiles**

Run: `./gradlew :core:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add core/
git commit -m "feat: add repository interfaces and Agent facade to core"
```

---

### Task 4: Create `session-repository-exposed` module

**Files:**
- Create: `session-repository-exposed/build.gradle.kts`
- Create: `session-repository-exposed/src/main/kotlin/com/ai/challenge/session/repository/SessionsTable.kt`
- Create: `session-repository-exposed/src/main/kotlin/com/ai/challenge/session/repository/ExposedSessionRepository.kt`
- Create: `session-repository-exposed/src/main/kotlin/com/ai/challenge/session/repository/DatabaseFactory.kt`
- Create: `session-repository-exposed/src/test/kotlin/com/ai/challenge/session/repository/ExposedSessionRepositoryTest.kt`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create `session-repository-exposed/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.ai.challenge"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)
    implementation(libs.kotlinx.datetime)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 2: Add to `settings.gradle.kts`**

Add `include("session-repository-exposed")` after the `include("core")` line.

- [ ] **Step 3: Create `DatabaseFactory.kt`**

```kotlin
package com.ai.challenge.session.repository

import org.jetbrains.exposed.sql.Database
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

fun createSessionDatabase(): Database {
    val dbDir = Path(System.getProperty("user.home"), ".ai-challenge")
    dbDir.createDirectories()
    val dbPath = dbDir.resolve("sessions.db")
    return Database.connect(
        url = "jdbc:sqlite:$dbPath",
        driver = "org.sqlite.JDBC",
        setupConnection = { conn ->
            conn.createStatement().execute("PRAGMA foreign_keys = ON")
        },
    )
}
```

- [ ] **Step 4: Create `SessionsTable.kt`**

```kotlin
package com.ai.challenge.session.repository

import org.jetbrains.exposed.sql.Table

object SessionsTable : Table("sessions") {
    val id = varchar("id", 36)
    val title = varchar("title", 255).default("")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}
```

- [ ] **Step 5: Create `ExposedSessionRepository.kt`**

```kotlin
package com.ai.challenge.session.repository

import com.ai.challenge.core.AgentSession
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.SessionRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedSessionRepository(private val database: Database) : SessionRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(SessionsTable)
        }
    }

    override suspend fun create(title: String): SessionId {
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

    override suspend fun get(id: SessionId): AgentSession? = transaction(database) {
        SessionsTable.selectAll()
            .where { SessionsTable.id eq id.value }
            .singleOrNull()
            ?.toAgentSession()
    }

    override suspend fun delete(id: SessionId): Boolean = transaction(database) {
        SessionsTable.deleteWhere { SessionsTable.id eq id.value } > 0
    }

    override suspend fun list(): List<AgentSession> = transaction(database) {
        SessionsTable.selectAll()
            .orderBy(SessionsTable.updatedAt, SortOrder.DESC)
            .map { it.toAgentSession() }
    }

    override suspend fun updateTitle(id: SessionId, title: String) {
        val now = Clock.System.now()
        transaction(database) {
            SessionsTable.update({ SessionsTable.id eq id.value }) {
                it[SessionsTable.title] = title
                it[updatedAt] = now.toEpochMilliseconds()
            }
        }
    }

    private fun ResultRow.toAgentSession() = AgentSession(
        id = SessionId(this[SessionsTable.id]),
        title = this[SessionsTable.title],
        createdAt = Instant.fromEpochMilliseconds(this[SessionsTable.createdAt]),
        updatedAt = Instant.fromEpochMilliseconds(this[SessionsTable.updatedAt]),
    )
}
```

Note: `AgentSession` no longer has `history`, so `getSession` doesn't load turns. Match `kotlinx.datetime` vs `kotlin.time` imports to what `core` uses.

- [ ] **Step 6: Write test — `ExposedSessionRepositoryTest.kt`**

```kotlin
package com.ai.challenge.session.repository

import com.ai.challenge.core.SessionId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedSessionRepositoryTest {

    private lateinit var repository: ExposedSessionRepository

    @BeforeTest
    fun setUp() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_session_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
            setupConnection = { conn ->
                conn.createStatement().execute("PRAGMA foreign_keys = ON")
            },
        )
        repository = ExposedSessionRepository(db)
    }

    @Test
    fun `create and get round-trip`() = runTest {
        val id = repository.create(title = "Test chat")
        val session = repository.get(id)
        assertNotNull(session)
        assertEquals("Test chat", session.title)
        assertEquals(id, session.id)
    }

    @Test
    fun `get returns null for unknown id`() = runTest {
        assertNull(repository.get(SessionId("nonexistent")))
    }

    @Test
    fun `delete removes session and returns true`() = runTest {
        val id = repository.create()
        assertTrue(repository.delete(id))
        assertNull(repository.get(id))
    }

    @Test
    fun `delete returns false for unknown id`() = runTest {
        assertFalse(repository.delete(SessionId("nonexistent")))
    }

    @Test
    fun `list returns all sessions sorted by updatedAt descending`() = runTest {
        val id1 = repository.create(title = "First")
        Thread.sleep(10) // ensure different timestamps
        val id2 = repository.create(title = "Second")

        val sessions = repository.list()
        assertEquals(2, sessions.size)
        assertEquals(id2, sessions[0].id)
        assertEquals(id1, sessions[1].id)
    }

    @Test
    fun `updateTitle changes session title`() = runTest {
        val id = repository.create(title = "Old")
        repository.updateTitle(id, "New")
        assertEquals("New", repository.get(id)?.title)
    }
}
```

- [ ] **Step 7: Add `kotlinx-coroutines-test` to `session-repository-exposed/build.gradle.kts` test dependencies**

Add this line to the dependencies block:

```kotlin
    testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 8: Run tests**

Run: `./gradlew :session-repository-exposed:test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 9: Commit**

```bash
git add session-repository-exposed/ settings.gradle.kts
git commit -m "feat: create session-repository-exposed module"
```

---

### Task 5: Create `turn-repository-exposed` module

**Files:**
- Create: `turn-repository-exposed/build.gradle.kts`
- Create: `turn-repository-exposed/src/main/kotlin/com/ai/challenge/turn/repository/TurnsTable.kt`
- Create: `turn-repository-exposed/src/main/kotlin/com/ai/challenge/turn/repository/ExposedTurnRepository.kt`
- Create: `turn-repository-exposed/src/main/kotlin/com/ai/challenge/turn/repository/DatabaseFactory.kt`
- Create: `turn-repository-exposed/src/test/kotlin/com/ai/challenge/turn/repository/ExposedTurnRepositoryTest.kt`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create `turn-repository-exposed/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.ai.challenge"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)
    implementation(libs.kotlinx.datetime)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 2: Add `include("turn-repository-exposed")` to `settings.gradle.kts`**

- [ ] **Step 3: Create `DatabaseFactory.kt`**

```kotlin
package com.ai.challenge.turn.repository

import org.jetbrains.exposed.sql.Database
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

fun createTurnDatabase(): Database {
    val dbDir = Path(System.getProperty("user.home"), ".ai-challenge")
    dbDir.createDirectories()
    val dbPath = dbDir.resolve("sessions.db")
    return Database.connect(
        url = "jdbc:sqlite:$dbPath",
        driver = "org.sqlite.JDBC",
        setupConnection = { conn ->
            conn.createStatement().execute("PRAGMA foreign_keys = ON")
        },
    )
}
```

- [ ] **Step 4: Create `TurnsTable.kt`**

```kotlin
package com.ai.challenge.turn.repository

import org.jetbrains.exposed.sql.Table

object TurnsTable : Table("turns") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val userMessage = text("user_message")
    val agentResponse = text("agent_response")
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)
}
```

Note: No foreign key to `SessionsTable` since this module is independent. The `sessionId` column is stored for querying but has no FK constraint.

- [ ] **Step 5: Create `ExposedTurnRepository.kt`**

```kotlin
package com.ai.challenge.turn.repository

import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn
import com.ai.challenge.core.TurnId
import com.ai.challenge.core.TurnRepository
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedTurnRepository(private val database: Database) : TurnRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(TurnsTable)
        }
    }

    override suspend fun append(sessionId: SessionId, turn: Turn): TurnId {
        transaction(database) {
            TurnsTable.insert {
                it[id] = turn.id.value
                it[TurnsTable.sessionId] = sessionId.value
                it[userMessage] = turn.userMessage
                it[agentResponse] = turn.agentResponse
                it[timestamp] = turn.timestamp.toEpochMilliseconds()
            }
        }
        return turn.id
    }

    override suspend fun getBySession(sessionId: SessionId, limit: Int?): List<Turn> = transaction(database) {
        val query = TurnsTable.selectAll()
            .where { TurnsTable.sessionId eq sessionId.value }
            .orderBy(TurnsTable.timestamp, SortOrder.ASC)

        val rows = if (limit != null) {
            val allRows = query.toList()
            if (allRows.size > limit) allRows.takeLast(limit) else allRows
        } else {
            query.toList()
        }

        rows.map { it.toTurn() }
    }

    override suspend fun get(turnId: TurnId): Turn? = transaction(database) {
        TurnsTable.selectAll()
            .where { TurnsTable.id eq turnId.value }
            .singleOrNull()
            ?.toTurn()
    }

    private fun ResultRow.toTurn() = Turn(
        id = TurnId(this[TurnsTable.id]),
        userMessage = this[TurnsTable.userMessage],
        agentResponse = this[TurnsTable.agentResponse],
        timestamp = Instant.fromEpochMilliseconds(this[TurnsTable.timestamp]),
    )
}
```

- [ ] **Step 6: Write test — `ExposedTurnRepositoryTest.kt`**

```kotlin
package com.ai.challenge.turn.repository

import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn
import com.ai.challenge.core.TurnId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedTurnRepositoryTest {

    private lateinit var repository: ExposedTurnRepository

    @BeforeTest
    fun setUp() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_turn_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        repository = ExposedTurnRepository(db)
    }

    @Test
    fun `append and getBySession round-trip`() = runTest {
        val sessionId = SessionId.generate()
        val turn = Turn(userMessage = "hi", agentResponse = "hello")
        repository.append(sessionId, turn)

        val history = repository.getBySession(sessionId)
        assertEquals(1, history.size)
        assertEquals("hi", history[0].userMessage)
        assertEquals("hello", history[0].agentResponse)
    }

    @Test
    fun `getBySession returns empty list for unknown session`() = runTest {
        assertTrue(repository.getBySession(SessionId("nonexistent")).isEmpty())
    }

    @Test
    fun `getBySession with limit returns last N turns`() = runTest {
        val sessionId = SessionId.generate()
        repository.append(sessionId, Turn(userMessage = "1", agentResponse = "a"))
        repository.append(sessionId, Turn(userMessage = "2", agentResponse = "b"))
        repository.append(sessionId, Turn(userMessage = "3", agentResponse = "c"))

        val history = repository.getBySession(sessionId, limit = 2)
        assertEquals(2, history.size)
        assertEquals("2", history[0].userMessage)
        assertEquals("3", history[1].userMessage)
    }

    @Test
    fun `get returns turn by id`() = runTest {
        val sessionId = SessionId.generate()
        val turn = Turn(userMessage = "hi", agentResponse = "hello")
        val turnId = repository.append(sessionId, turn)

        val result = repository.get(turnId)
        assertNotNull(result)
        assertEquals("hi", result.userMessage)
    }

    @Test
    fun `get returns null for unknown turnId`() = runTest {
        assertNull(repository.get(TurnId("nonexistent")))
    }

    @Test
    fun `getBySession does not include turns from other sessions`() = runTest {
        val session1 = SessionId.generate()
        val session2 = SessionId.generate()
        repository.append(session1, Turn(userMessage = "a", agentResponse = "b"))
        repository.append(session2, Turn(userMessage = "c", agentResponse = "d"))

        val result = repository.getBySession(session1)
        assertEquals(1, result.size)
        assertEquals("a", result[0].userMessage)
    }
}
```

- [ ] **Step 7: Run tests**

Run: `./gradlew :turn-repository-exposed:test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 8: Commit**

```bash
git add turn-repository-exposed/ settings.gradle.kts
git commit -m "feat: create turn-repository-exposed module"
```

---

### Task 6: Create `token-repository-exposed` module

**Files:**
- Create: `token-repository-exposed/build.gradle.kts`
- Create: `token-repository-exposed/src/main/kotlin/com/ai/challenge/token/repository/TokenDetailsTable.kt`
- Create: `token-repository-exposed/src/main/kotlin/com/ai/challenge/token/repository/ExposedTokenRepository.kt`
- Create: `token-repository-exposed/src/main/kotlin/com/ai/challenge/token/repository/DatabaseFactory.kt`
- Create: `token-repository-exposed/src/test/kotlin/com/ai/challenge/token/repository/ExposedTokenRepositoryTest.kt`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create `token-repository-exposed/build.gradle.kts`**

Same as turn-repository-exposed (copy pattern):

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.ai.challenge"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 2: Add `include("token-repository-exposed")` to `settings.gradle.kts`**

- [ ] **Step 3: Create `DatabaseFactory.kt`**

```kotlin
package com.ai.challenge.token.repository

import org.jetbrains.exposed.sql.Database
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

fun createTokenDatabase(): Database {
    val dbDir = Path(System.getProperty("user.home"), ".ai-challenge")
    dbDir.createDirectories()
    val dbPath = dbDir.resolve("sessions.db")
    return Database.connect(
        url = "jdbc:sqlite:$dbPath",
        driver = "org.sqlite.JDBC",
    )
}
```

- [ ] **Step 4: Create `TokenDetailsTable.kt`**

```kotlin
package com.ai.challenge.token.repository

import org.jetbrains.exposed.sql.Table

object TokenDetailsTable : Table("token_details") {
    val turnId = varchar("turn_id", 36)
    val sessionId = varchar("session_id", 36)
    val promptTokens = integer("prompt_tokens")
    val completionTokens = integer("completion_tokens")
    val cachedTokens = integer("cached_tokens")
    val cacheWriteTokens = integer("cache_write_tokens")
    val reasoningTokens = integer("reasoning_tokens")

    override val primaryKey = PrimaryKey(turnId)
}
```

- [ ] **Step 5: Create `ExposedTokenRepository.kt`**

```kotlin
package com.ai.challenge.token.repository

import com.ai.challenge.core.SessionId
import com.ai.challenge.core.TokenDetails
import com.ai.challenge.core.TokenRepository
import com.ai.challenge.core.TurnId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedTokenRepository(private val database: Database) : TokenRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(TokenDetailsTable)
        }
    }

    override suspend fun record(sessionId: SessionId, turnId: TurnId, details: TokenDetails) {
        transaction(database) {
            TokenDetailsTable.insert {
                it[TokenDetailsTable.turnId] = turnId.value
                it[TokenDetailsTable.sessionId] = sessionId.value
                it[promptTokens] = details.promptTokens
                it[completionTokens] = details.completionTokens
                it[cachedTokens] = details.cachedTokens
                it[cacheWriteTokens] = details.cacheWriteTokens
                it[reasoningTokens] = details.reasoningTokens
            }
        }
    }

    override suspend fun getByTurn(turnId: TurnId): TokenDetails? = transaction(database) {
        TokenDetailsTable.selectAll()
            .where { TokenDetailsTable.turnId eq turnId.value }
            .singleOrNull()
            ?.toTokenDetails()
    }

    override suspend fun getBySession(sessionId: SessionId): Map<TurnId, TokenDetails> = transaction(database) {
        TokenDetailsTable.selectAll()
            .where { TokenDetailsTable.sessionId eq sessionId.value }
            .associate { row ->
                TurnId(row[TokenDetailsTable.turnId]) to row.toTokenDetails()
            }
    }

    override suspend fun getSessionTotal(sessionId: SessionId): TokenDetails =
        getBySession(sessionId).values.fold(TokenDetails()) { acc, t -> acc + t }

    private fun ResultRow.toTokenDetails() = TokenDetails(
        promptTokens = this[TokenDetailsTable.promptTokens],
        completionTokens = this[TokenDetailsTable.completionTokens],
        cachedTokens = this[TokenDetailsTable.cachedTokens],
        cacheWriteTokens = this[TokenDetailsTable.cacheWriteTokens],
        reasoningTokens = this[TokenDetailsTable.reasoningTokens],
    )
}
```

- [ ] **Step 6: Write test — `ExposedTokenRepositoryTest.kt`**

```kotlin
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
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()

        repository.record(session1, turnId1, TokenDetails(promptTokens = 10))
        repository.record(session2, turnId2, TokenDetails(promptTokens = 20))

        val result = repository.getBySession(session1)
        assertEquals(1, result.size)
        assertEquals(10, result[turnId1]!!.promptTokens)
    }

    @Test
    fun `getSessionTotal returns accumulated tokens`() = runTest {
        val sessionId = SessionId.generate()
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()

        repository.record(sessionId, turnId1, TokenDetails(promptTokens = 10, completionTokens = 5))
        repository.record(sessionId, turnId2, TokenDetails(promptTokens = 20, completionTokens = 10))

        val total = repository.getSessionTotal(sessionId)
        assertEquals(30, total.promptTokens)
        assertEquals(15, total.completionTokens)
    }

    @Test
    fun `getSessionTotal returns empty for session with no tokens`() = runTest {
        assertEquals(TokenDetails(), repository.getSessionTotal(SessionId.generate()))
    }
}
```

- [ ] **Step 7: Run tests**

Run: `./gradlew :token-repository-exposed:test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 8: Commit**

```bash
git add token-repository-exposed/ settings.gradle.kts
git commit -m "feat: create token-repository-exposed module"
```

---

### Task 7: Create `cost-repository-exposed` module

**Files:**
- Create: `cost-repository-exposed/build.gradle.kts`
- Create: `cost-repository-exposed/src/main/kotlin/com/ai/challenge/cost/repository/CostDetailsTable.kt`
- Create: `cost-repository-exposed/src/main/kotlin/com/ai/challenge/cost/repository/ExposedCostRepository.kt`
- Create: `cost-repository-exposed/src/main/kotlin/com/ai/challenge/cost/repository/DatabaseFactory.kt`
- Create: `cost-repository-exposed/src/test/kotlin/com/ai/challenge/cost/repository/ExposedCostRepositoryTest.kt`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create `cost-repository-exposed/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.ai.challenge"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 2: Add `include("cost-repository-exposed")` to `settings.gradle.kts`**

- [ ] **Step 3: Create `DatabaseFactory.kt`**

```kotlin
package com.ai.challenge.cost.repository

import org.jetbrains.exposed.sql.Database
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

fun createCostDatabase(): Database {
    val dbDir = Path(System.getProperty("user.home"), ".ai-challenge")
    dbDir.createDirectories()
    val dbPath = dbDir.resolve("sessions.db")
    return Database.connect(
        url = "jdbc:sqlite:$dbPath",
        driver = "org.sqlite.JDBC",
    )
}
```

- [ ] **Step 4: Create `CostDetailsTable.kt`**

```kotlin
package com.ai.challenge.cost.repository

import org.jetbrains.exposed.sql.Table

object CostDetailsTable : Table("cost_details") {
    val turnId = varchar("turn_id", 36)
    val sessionId = varchar("session_id", 36)
    val totalCost = double("total_cost")
    val upstreamCost = double("upstream_cost")
    val upstreamPromptCost = double("upstream_prompt_cost")
    val upstreamCompletionsCost = double("upstream_completions_cost")

    override val primaryKey = PrimaryKey(turnId)
}
```

- [ ] **Step 5: Create `ExposedCostRepository.kt`**

```kotlin
package com.ai.challenge.cost.repository

import com.ai.challenge.core.CostDetails
import com.ai.challenge.core.CostRepository
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.TurnId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedCostRepository(private val database: Database) : CostRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(CostDetailsTable)
        }
    }

    override suspend fun record(sessionId: SessionId, turnId: TurnId, details: CostDetails) {
        transaction(database) {
            CostDetailsTable.insert {
                it[CostDetailsTable.turnId] = turnId.value
                it[CostDetailsTable.sessionId] = sessionId.value
                it[totalCost] = details.totalCost
                it[upstreamCost] = details.upstreamCost
                it[upstreamPromptCost] = details.upstreamPromptCost
                it[upstreamCompletionsCost] = details.upstreamCompletionsCost
            }
        }
    }

    override suspend fun getByTurn(turnId: TurnId): CostDetails? = transaction(database) {
        CostDetailsTable.selectAll()
            .where { CostDetailsTable.turnId eq turnId.value }
            .singleOrNull()
            ?.toCostDetails()
    }

    override suspend fun getBySession(sessionId: SessionId): Map<TurnId, CostDetails> = transaction(database) {
        CostDetailsTable.selectAll()
            .where { CostDetailsTable.sessionId eq sessionId.value }
            .associate { row ->
                TurnId(row[CostDetailsTable.turnId]) to row.toCostDetails()
            }
    }

    override suspend fun getSessionTotalCost(sessionId: SessionId): CostDetails =
        getBySession(sessionId).values.fold(CostDetails()) { acc, c -> acc + c }

    private fun ResultRow.toCostDetails() = CostDetails(
        totalCost = this[CostDetailsTable.totalCost],
        upstreamCost = this[CostDetailsTable.upstreamCost],
        upstreamPromptCost = this[CostDetailsTable.upstreamPromptCost],
        upstreamCompletionsCost = this[CostDetailsTable.upstreamCompletionsCost],
    )
}
```

**IMPORTANT:** Check that the `CostRepository` interface method is named `getSessionTotal` (not `getSessionTotalCost`). The spec defines it as `getSessionTotal`. Adjust the override method name to match the interface:

```kotlin
    override suspend fun getSessionTotal(sessionId: SessionId): CostDetails =
        getBySession(sessionId).values.fold(CostDetails()) { acc, c -> acc + c }
```

- [ ] **Step 6: Write test — `ExposedCostRepositoryTest.kt`**

```kotlin
package com.ai.challenge.cost.repository

import com.ai.challenge.core.CostDetails
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.TurnId
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
```

- [ ] **Step 7: Run tests**

Run: `./gradlew :cost-repository-exposed:test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 8: Commit**

```bash
git add cost-repository-exposed/ settings.gradle.kts
git commit -m "feat: create cost-repository-exposed module"
```

---

### Task 8: Update `ai-agent` module — new Agent implementation

**Files:**
- Modify: `ai-agent/build.gradle.kts`
- Delete: `ai-agent/src/main/kotlin/com/ai/challenge/agent/Agent.kt`
- Delete: `ai-agent/src/main/kotlin/com/ai/challenge/agent/AgentError.kt`
- Delete: `ai-agent/src/main/kotlin/com/ai/challenge/agent/AgentResponse.kt`
- Modify: `ai-agent/src/main/kotlin/com/ai/challenge/agent/OpenRouterAgent.kt`
- Modify: `ai-agent/src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt`

- [ ] **Step 1: Update `ai-agent/build.gradle.kts` — replace `session-storage` with `core`**

Replace the dependencies block:

```kotlin
dependencies {
    implementation(project(":llm-service"))
    implementation(project(":core"))
    implementation(libs.arrow.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
}
```

- [ ] **Step 2: Delete `Agent.kt`, `AgentError.kt`, `AgentResponse.kt`** from `ai-agent/src/main/kotlin/com/ai/challenge/agent/`

These are now in `core` module. Delete:
- `ai-agent/src/main/kotlin/com/ai/challenge/agent/Agent.kt`
- `ai-agent/src/main/kotlin/com/ai/challenge/agent/AgentError.kt`
- `ai-agent/src/main/kotlin/com/ai/challenge/agent/AgentResponse.kt`

- [ ] **Step 3: Rewrite `OpenRouterAgent.kt`**

```kotlin
package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.ai.challenge.core.Agent
import com.ai.challenge.core.AgentError
import com.ai.challenge.core.AgentResponse
import com.ai.challenge.core.AgentSession
import com.ai.challenge.core.CostDetails
import com.ai.challenge.core.CostRepository
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.SessionRepository
import com.ai.challenge.core.TokenDetails
import com.ai.challenge.core.TokenRepository
import com.ai.challenge.core.Turn
import com.ai.challenge.core.TurnId
import com.ai.challenge.core.TurnRepository
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.llm.model.ChatResponse

class OpenRouterAgent(
    private val service: OpenRouterService,
    private val model: String,
    private val sessionRepository: SessionRepository,
    private val turnRepository: TurnRepository,
    private val tokenRepository: TokenRepository,
    private val costRepository: CostRepository,
) : Agent {

    override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> = either {
        val history = turnRepository.getBySession(sessionId)

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

        val tokenDetails = chatResponse.toTokenDetails()
        val costDetails = chatResponse.toCostDetails()
        val turn = Turn(userMessage = message, agentResponse = text)
        val turnId = turnRepository.append(sessionId, turn)
        tokenRepository.record(sessionId, turnId, tokenDetails)
        costRepository.record(sessionId, turnId, costDetails)

        AgentResponse(text = text, turnId = turnId, tokenDetails = tokenDetails, costDetails = costDetails)
    }

    override suspend fun createSession(title: String): SessionId = sessionRepository.create(title)
    override suspend fun deleteSession(id: SessionId): Boolean = sessionRepository.delete(id)
    override suspend fun listSessions(): List<AgentSession> = sessionRepository.list()
    override suspend fun getSession(id: SessionId): AgentSession? = sessionRepository.get(id)
    override suspend fun getTurns(sessionId: SessionId, limit: Int?): List<Turn> = turnRepository.getBySession(sessionId, limit)
    override suspend fun getTokensByTurn(turnId: TurnId): TokenDetails? = tokenRepository.getByTurn(turnId)
    override suspend fun getTokensBySession(sessionId: SessionId): Map<TurnId, TokenDetails> = tokenRepository.getBySession(sessionId)
    override suspend fun getSessionTotalTokens(sessionId: SessionId): TokenDetails = tokenRepository.getSessionTotal(sessionId)
    override suspend fun getCostByTurn(turnId: TurnId): CostDetails? = costRepository.getByTurn(turnId)
    override suspend fun getCostBySession(sessionId: SessionId): Map<TurnId, CostDetails> = costRepository.getBySession(sessionId)
    override suspend fun getSessionTotalCost(sessionId: SessionId): CostDetails = costRepository.getSessionTotal(sessionId)
}

private fun ChatResponse.toTokenDetails(): TokenDetails = TokenDetails(
    promptTokens = usage?.promptTokens ?: 0,
    completionTokens = usage?.completionTokens ?: 0,
    cachedTokens = usage?.promptTokensDetails?.cachedTokens ?: 0,
    cacheWriteTokens = usage?.promptTokensDetails?.cacheWriteTokens ?: 0,
    reasoningTokens = usage?.completionTokensDetails?.reasoningTokens ?: 0,
)

private fun ChatResponse.toCostDetails(): CostDetails = CostDetails(
    totalCost = usage?.cost ?: cost ?: 0.0,
    upstreamCost = usage?.costDetails?.upstreamCost ?: costDetails?.upstreamCost ?: 0.0,
    upstreamPromptCost = usage?.costDetails?.upstreamPromptCost ?: costDetails?.upstreamPromptCost ?: 0.0,
    upstreamCompletionsCost = usage?.costDetails?.upstreamCompletionsCost ?: costDetails?.upstreamCompletionsCost ?: 0.0,
)
```

- [ ] **Step 4: Rewrite `OpenRouterAgentTest.kt`**

The tests need to use fake repositories instead of InMemorySessionManager/InMemoryUsageManager. Create simple in-test fakes:

```kotlin
package com.ai.challenge.agent

import arrow.core.Either
import com.ai.challenge.core.Agent
import com.ai.challenge.core.AgentError
import com.ai.challenge.core.AgentResponse
import com.ai.challenge.core.AgentSession
import com.ai.challenge.core.CostDetails
import com.ai.challenge.core.CostRepository
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.SessionRepository
import com.ai.challenge.core.TokenDetails
import com.ai.challenge.core.TokenRepository
import com.ai.challenge.core.Turn
import com.ai.challenge.core.TurnId
import com.ai.challenge.core.TurnRepository
import com.ai.challenge.llm.OpenRouterService
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenRouterAgentTest {

    private val sessionRepo = FakeSessionRepository()
    private val turnRepo = FakeTurnRepository()
    private val tokenRepo = FakeTokenRepository()
    private val costRepo = FakeCostRepository()

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
        OpenRouterService(apiKey = "test-key", client = createMockClient(responseJson))

    private fun createAgent(responseJson: String): OpenRouterAgent =
        OpenRouterAgent(
            service = createService(responseJson),
            model = "test-model",
            sessionRepository = sessionRepo,
            turnRepository = turnRepo,
            tokenRepository = tokenRepo,
            costRepository = costRepo,
        )

    @Test
    fun `send returns Right with response text on success`() = runTest {
        val agent = createAgent("""{"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}""")
        val sessionId = sessionRepo.create()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<AgentResponse>>(result)
        assertEquals("Hello!", result.value.text)
    }

    @Test
    fun `send returns AgentResponse with full token and cost details`() = runTest {
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
        val sessionId = sessionRepo.create()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<AgentResponse>>(result)
        assertEquals(TokenDetails(promptTokens = 100, completionTokens = 50, cachedTokens = 20, cacheWriteTokens = 80, reasoningTokens = 10), result.value.tokenDetails)
        assertEquals(0.0015, result.value.costDetails.totalCost, 1e-9)
        assertEquals(0.0012, result.value.costDetails.upstreamCost, 1e-9)
        assertEquals(0.0008, result.value.costDetails.upstreamPromptCost, 1e-9)
        assertEquals(0.0004, result.value.costDetails.upstreamCompletionsCost, 1e-9)
    }

    @Test
    fun `send returns default details when usage is null`() = runTest {
        val agent = createAgent("""{"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}""")
        val sessionId = sessionRepo.create()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<AgentResponse>>(result)
        assertEquals(TokenDetails(), result.value.tokenDetails)
        assertEquals(CostDetails(), result.value.costDetails)
    }

    @Test
    fun `send persists token and cost details`() = runTest {
        val agent = createAgent("""
            {
              "choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}],
              "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}
            }
        """.trimIndent())
        val sessionId = sessionRepo.create()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<AgentResponse>>(result)
        val storedTokens = tokenRepo.getByTurn(result.value.turnId)
        assertNotNull(storedTokens)
        assertEquals(10, storedTokens.promptTokens)
        assertEquals(5, storedTokens.completionTokens)

        val storedCost = costRepo.getByTurn(result.value.turnId)
        assertNotNull(storedCost)
    }

    @Test
    fun `send saves turn on success`() = runTest {
        val agent = createAgent("""{"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}""")
        val sessionId = sessionRepo.create()

        agent.send(sessionId, "Hi")

        val history = turnRepo.getBySession(sessionId)
        assertEquals(1, history.size)
        assertEquals("Hi", history[0].userMessage)
        assertEquals("Hello!", history[0].agentResponse)
    }

    @Test
    fun `send does not save turn on failure`() = runTest {
        val agent = createAgent("""{"error":{"message":"Rate limit exceeded","code":429},"choices":[]}""")
        val sessionId = sessionRepo.create()

        agent.send(sessionId, "Hi")

        val history = turnRepo.getBySession(sessionId)
        assertTrue(history.isEmpty())
    }

    @Test
    fun `send returns Left ApiError when response has error`() = runTest {
        val agent = createAgent("""{"error":{"message":"Rate limit exceeded","code":429},"choices":[]}""")
        val sessionId = sessionRepo.create()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Left<AgentError>>(result)
        assertIs<AgentError.ApiError>(result.value)
        assertEquals("Rate limit exceeded", result.value.message)
    }

    @Test
    fun `send returns Left NetworkError when service throws`() = runTest {
        val mockEngine = MockEngine { _ -> throw RuntimeException("Connection refused") }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = false }) }
        }
        val service = OpenRouterService(apiKey = "test-key", client = client)
        val agent = OpenRouterAgent(service, "test-model", sessionRepo, turnRepo, tokenRepo, costRepo)
        val sessionId = sessionRepo.create()

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
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = false }) }
        }
        val service = OpenRouterService(apiKey = "test-key", client = client)
        val agent = OpenRouterAgent(service, "test-model", sessionRepo, turnRepo, tokenRepo, costRepo)
        val sessionId = sessionRepo.create()

        turnRepo.append(sessionId, Turn(userMessage = "Hi", agentResponse = "Hello!"))

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

// --- Test fakes ---

private class FakeSessionRepository : SessionRepository {
    private val sessions = ConcurrentHashMap<SessionId, AgentSession>()

    override suspend fun create(title: String): SessionId {
        val id = SessionId.generate()
        sessions[id] = AgentSession(id = id, title = title)
        return id
    }
    override suspend fun get(id: SessionId): AgentSession? = sessions[id]
    override suspend fun delete(id: SessionId): Boolean = sessions.remove(id) != null
    override suspend fun list(): List<AgentSession> = sessions.values.toList()
    override suspend fun updateTitle(id: SessionId, title: String) {
        sessions.computeIfPresent(id) { _, s -> s.copy(title = title) }
    }
}

private class FakeTurnRepository : TurnRepository {
    private val turns = ConcurrentHashMap<TurnId, Pair<SessionId, Turn>>()

    override suspend fun append(sessionId: SessionId, turn: Turn): TurnId {
        turns[turn.id] = sessionId to turn
        return turn.id
    }
    override suspend fun getBySession(sessionId: SessionId, limit: Int?): List<Turn> {
        val all = turns.values.filter { it.first == sessionId }.map { it.second }.sortedBy { it.timestamp }
        return if (limit != null && all.size > limit) all.takeLast(limit) else all
    }
    override suspend fun get(turnId: TurnId): Turn? = turns[turnId]?.second
}

private class FakeTokenRepository : TokenRepository {
    private val data = ConcurrentHashMap<TurnId, Pair<SessionId, TokenDetails>>()

    override suspend fun record(sessionId: SessionId, turnId: TurnId, details: TokenDetails) { data[turnId] = sessionId to details }
    override suspend fun getByTurn(turnId: TurnId): TokenDetails? = data[turnId]?.second
    override suspend fun getBySession(sessionId: SessionId): Map<TurnId, TokenDetails> =
        data.filter { it.value.first == sessionId }.mapValues { it.value.second }
    override suspend fun getSessionTotal(sessionId: SessionId): TokenDetails =
        getBySession(sessionId).values.fold(TokenDetails()) { acc, t -> acc + t }
}

private class FakeCostRepository : CostRepository {
    private val data = ConcurrentHashMap<TurnId, Pair<SessionId, CostDetails>>()

    override suspend fun record(sessionId: SessionId, turnId: TurnId, details: CostDetails) { data[turnId] = sessionId to details }
    override suspend fun getByTurn(turnId: TurnId): CostDetails? = data[turnId]?.second
    override suspend fun getBySession(sessionId: SessionId): Map<TurnId, CostDetails> =
        data.filter { it.value.first == sessionId }.mapValues { it.value.second }
    override suspend fun getSessionTotal(sessionId: SessionId): CostDetails =
        getBySession(sessionId).values.fold(CostDetails()) { acc, c -> acc + c }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :ai-agent:test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 6: Commit**

```bash
git add ai-agent/
git commit -m "feat: update ai-agent to use core module and new repositories"
```

---

### Task 9: Update `compose-ui` — use Agent as sole data source

**Files:**
- Modify: `compose-ui/build.gradle.kts`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/di/AppModule.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/main.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootComponent.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatComponent.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/sessionlist/store/SessionListStore.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/sessionlist/store/SessionListStoreFactory.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/model/UiMessage.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootContent.kt`
- Modify: `compose-ui/src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt`
- Modify: `compose-ui/src/test/kotlin/com/ai/challenge/ui/sessionlist/store/SessionListStoreTest.kt`

- [ ] **Step 1: Update `compose-ui/build.gradle.kts`**

Replace the dependencies block. Remove `session-storage`, add `core` and 4 repository modules. Remove direct `exposed`/`sqlite` deps (now in repository modules):

```kotlin
dependencies {
    implementation(project(":ai-agent"))
    implementation(project(":llm-service"))
    implementation(project(":core"))
    implementation(project(":session-repository-exposed"))
    implementation(project(":turn-repository-exposed"))
    implementation(project(":token-repository-exposed"))
    implementation(project(":cost-repository-exposed"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation(libs.decompose)
    implementation(libs.decompose.extensions.compose)
    implementation(libs.mvikotlin)
    implementation(libs.mvikotlin.main)
    implementation(libs.mvikotlin.extensions.coroutines)
    implementation(libs.koin.core)
    implementation(libs.arrow.core)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.datetime)
}
```

- [ ] **Step 2: Rewrite `AppModule.kt`**

```kotlin
package com.ai.challenge.ui.di

import com.ai.challenge.agent.OpenRouterAgent
import com.ai.challenge.core.Agent
import com.ai.challenge.core.CostRepository
import com.ai.challenge.core.SessionRepository
import com.ai.challenge.core.TokenRepository
import com.ai.challenge.core.TurnRepository
import com.ai.challenge.cost.repository.ExposedCostRepository
import com.ai.challenge.cost.repository.createCostDatabase
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.session.repository.ExposedSessionRepository
import com.ai.challenge.session.repository.createSessionDatabase
import com.ai.challenge.token.repository.ExposedTokenRepository
import com.ai.challenge.token.repository.createTokenDatabase
import com.ai.challenge.turn.repository.ExposedTurnRepository
import com.ai.challenge.turn.repository.createTurnDatabase
import org.koin.dsl.module

val appModule = module {
    single {
        OpenRouterService(
            apiKey = System.getenv("OPENROUTER_API_KEY")
                ?: error("OPENROUTER_API_KEY environment variable is not set"),
        )
    }
    single<SessionRepository> { ExposedSessionRepository(createSessionDatabase()) }
    single<TurnRepository> { ExposedTurnRepository(createTurnDatabase()) }
    single<TokenRepository> { ExposedTokenRepository(createTokenDatabase()) }
    single<CostRepository> { ExposedCostRepository(createCostDatabase()) }
    single<Agent> {
        OpenRouterAgent(
            service = get(),
            model = "google/gemini-2.0-flash-001",
            sessionRepository = get(),
            turnRepository = get(),
            tokenRepository = get(),
            costRepository = get(),
        )
    }
}
```

- [ ] **Step 3: Rewrite `main.kt`** — remove `AgentSessionManager`, `UsageManager` deps

```kotlin
package com.ai.challenge.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ai.challenge.core.Agent
import com.ai.challenge.ui.di.appModule
import com.ai.challenge.ui.root.RootComponent
import com.ai.challenge.ui.root.RootContent
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import org.koin.core.context.startKoin

fun main() {
    val koin = startKoin {
        modules(appModule)
    }.koin

    val lifecycle = LifecycleRegistry()

    val root = runOnUiThread {
        RootComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            storeFactory = DefaultStoreFactory(),
            agent = koin.get<Agent>(),
        )
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "AI Chat",
        ) {
            MaterialTheme {
                RootContent(root)
            }
        }
    }
}
```

- [ ] **Step 4: Rewrite `RootComponent.kt`** — use only `Agent`

```kotlin
package com.ai.challenge.ui.root

import com.ai.challenge.core.Agent
import com.ai.challenge.core.SessionId
import com.ai.challenge.ui.chat.ChatComponent
import com.ai.challenge.ui.sessionlist.store.SessionListStore
import com.ai.challenge.ui.sessionlist.store.SessionListStoreFactory
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceCurrent
import com.arkivanov.decompose.value.Value
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

class RootComponent(
    componentContext: ComponentContext,
    private val storeFactory: StoreFactory,
    private val agent: Agent,
) : ComponentContext by componentContext {

    private val sessionListStore = instanceKeeper.getStore {
        SessionListStoreFactory(storeFactory, agent).create()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionListState: StateFlow<SessionListStore.State> = sessionListStore.stateFlow

    private val navigation = StackNavigation<Config>()

    val childStack: Value<ChildStack<*, Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = Config.Chat(sessionId = ""),
            handleBackButton = false,
            childFactory = ::createChild,
        )

    init {
        runBlocking {
            val sessions = agent.listSessions()
            if (sessions.isEmpty()) {
                val id = agent.createSession()
                sessionListStore.accept(SessionListStore.Intent.LoadSessions)
                selectSession(id)
            } else {
                sessionListStore.accept(SessionListStore.Intent.LoadSessions)
                selectSession(sessions.first().id)
            }
        }
    }

    fun selectSession(sessionId: SessionId) {
        sessionListStore.accept(SessionListStore.Intent.SelectSession(sessionId))
        navigation.replaceCurrent(Config.Chat(sessionId = sessionId.value))
    }

    fun createNewSession() {
        runBlocking {
            val id = agent.createSession()
            sessionListStore.accept(SessionListStore.Intent.LoadSessions)
            selectSession(id)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun deleteSession(sessionId: SessionId) {
        runBlocking {
            agent.deleteSession(sessionId)
            sessionListStore.accept(SessionListStore.Intent.LoadSessions)

            val remaining = agent.listSessions()
            if (remaining.isEmpty()) {
                createNewSession()
            } else {
                val currentActive = sessionListStore.stateFlow.value.activeSessionId
                if (currentActive == sessionId) {
                    selectSession(remaining.first().id)
                }
            }
        }
    }

    private fun createChild(config: Config, componentContext: ComponentContext): Child =
        when (config) {
            is Config.Chat -> Child.Chat(
                ChatComponent(
                    componentContext = componentContext,
                    storeFactory = storeFactory,
                    agent = agent,
                    sessionId = SessionId(config.sessionId),
                )
            )
        }

    sealed interface Child {
        data class Chat(val component: ChatComponent) : Child
    }

    @Serializable
    sealed interface Config {
        @Serializable
        data class Chat(val sessionId: String) : Config
    }
}
```

Note: `RootComponent` now uses `runBlocking` for init-time suspend calls (createSession, listSessions, deleteSession). These are fast local DB operations on the main thread, which matches the original synchronous behavior.

- [ ] **Step 5: Rewrite `ChatComponent.kt`** — remove `sessionManager`, `usageManager`

```kotlin
package com.ai.challenge.ui.chat

import com.ai.challenge.core.Agent
import com.ai.challenge.core.SessionId
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
    sessionId: SessionId,
) : ComponentContext by componentContext {

    private val store = instanceKeeper.getStore {
        ChatStoreFactory(storeFactory, agent).create()
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

- [ ] **Step 6: Rewrite `ChatStore.kt`** — replace `RequestMetrics` with `TokenDetails` and `CostDetails`

```kotlin
package com.ai.challenge.ui.chat.store

import com.ai.challenge.core.CostDetails
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.TokenDetails
import com.ai.challenge.core.TurnId
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
        val turnTokens: Map<TurnId, TokenDetails> = emptyMap(),
        val turnCosts: Map<TurnId, CostDetails> = emptyMap(),
        val sessionTokens: TokenDetails = TokenDetails(),
        val sessionCosts: CostDetails = CostDetails(),
    )
}
```

- [ ] **Step 7: Rewrite `ChatStoreFactory.kt`** — use only `Agent`

```kotlin
package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.core.Agent
import com.ai.challenge.core.CostDetails
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.TokenDetails
import com.ai.challenge.core.TurnId
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class ChatStoreFactory(
    private val storeFactory: StoreFactory,
    private val agent: Agent,
) {
    fun create(): ChatStore =
        object : ChatStore,
            Store<ChatStore.Intent, ChatStore.State, Nothing> by storeFactory.create(
                name = "ChatStore",
                initialState = ChatStore.State(),
                executorFactory = { ExecutorImpl(agent) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class SessionLoaded(
            val sessionId: SessionId,
            val messages: List<UiMessage>,
            val turnTokens: Map<TurnId, TokenDetails>,
            val turnCosts: Map<TurnId, CostDetails>,
            val sessionTokens: TokenDetails,
            val sessionCosts: CostDetails,
        ) : Msg
        data class UserMessage(val text: String) : Msg
        data class AgentResponseMsg(
            val text: String,
            val turnId: TurnId,
            val tokenDetails: TokenDetails,
            val costDetails: CostDetails,
        ) : Msg
        data class Error(val text: String) : Msg
        data object Loading : Msg
        data object LoadingComplete : Msg
    }

    private class ExecutorImpl(
        private val agent: Agent,
    ) : CoroutineExecutor<ChatStore.Intent, Nothing, ChatStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: ChatStore.Intent) {
            when (intent) {
                is ChatStore.Intent.SendMessage -> handleSendMessage(intent.text)
                is ChatStore.Intent.LoadSession -> handleLoadSession(intent.sessionId)
            }
        }

        private fun handleLoadSession(sessionId: SessionId) {
            scope.launch {
                val history = agent.getTurns(sessionId)
                val messages = history.flatMap { turn ->
                    listOf(
                        UiMessage(text = turn.userMessage, isUser = true, turnId = turn.id),
                        UiMessage(text = turn.agentResponse, isUser = false, turnId = turn.id),
                    )
                }
                val turnTokens = agent.getTokensBySession(sessionId)
                val turnCosts = agent.getCostBySession(sessionId)
                val sessionTokens = turnTokens.values.fold(TokenDetails()) { acc, t -> acc + t }
                val sessionCosts = turnCosts.values.fold(CostDetails()) { acc, c -> acc + c }
                dispatch(Msg.SessionLoaded(sessionId, messages, turnTokens, turnCosts, sessionTokens, sessionCosts))
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
                            tokenDetails = result.value.tokenDetails,
                            costDetails = result.value.costDetails,
                        )
                    )
                    is Either.Left -> dispatch(Msg.Error(result.value.message))
                }
                dispatch(Msg.LoadingComplete)

                val session = agent.getSession(sessionId)
                if (session != null && session.title.isEmpty()) {
                    val title = text.take(50)
                    agent.createSession(title) // Wrong — should update title, not create
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
                    turnTokens = msg.turnTokens,
                    turnCosts = msg.turnCosts,
                    sessionTokens = msg.sessionTokens,
                    sessionCosts = msg.sessionCosts,
                )
                is Msg.UserMessage -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = true),
                )
                is Msg.AgentResponseMsg -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false, turnId = msg.turnId),
                    turnTokens = turnTokens + (msg.turnId to msg.tokenDetails),
                    turnCosts = turnCosts + (msg.turnId to msg.costDetails),
                    sessionTokens = sessionTokens + msg.tokenDetails,
                    sessionCosts = sessionCosts + msg.costDetails,
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

**IMPORTANT FIX:** The auto-title logic in `handleSendMessage` has a bug above. It should call a method on Agent to update the title. But the `Agent` interface doesn't have `updateTitle` — only `SessionRepository` does. However, `Agent` has `createSession(title)` which is wrong here. We need to either:
- Add `updateSessionTitle` to Agent interface, or
- Use the existing approach differently.

Looking at the spec, Agent should be the sole data source. The original code calls `sessionManager.updateTitle()`. Since Agent doesn't expose `updateTitle`, we have two choices. The simplest is to not auto-title from the store (it could be done inside `OpenRouterAgent.send()`). But the original behavior titles from the store.

**Resolution:** Add `updateSessionTitle` to the Agent interface in `core`. This was missed in the spec. The corrected `handleSendMessage`:

```kotlin
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
                            tokenDetails = result.value.tokenDetails,
                            costDetails = result.value.costDetails,
                        )
                    )
                    is Either.Left -> dispatch(Msg.Error(result.value.message))
                }
                dispatch(Msg.LoadingComplete)

                val session = agent.getSession(sessionId)
                if (session != null && session.title.isEmpty()) {
                    agent.updateSessionTitle(sessionId, text.take(50))
                }
            }
        }
```

This means we need to:
1. Add `suspend fun updateSessionTitle(id: SessionId, title: String)` to `Agent` interface in `core/src/main/kotlin/com/ai/challenge/core/Agent.kt`
2. Add delegation in `OpenRouterAgent`: `override suspend fun updateSessionTitle(id: SessionId, title: String) = sessionRepository.updateTitle(id, title)`

- [ ] **Step 8: Add `updateSessionTitle` to Agent interface in core**

Add to `core/src/main/kotlin/com/ai/challenge/core/Agent.kt`:

```kotlin
    suspend fun updateSessionTitle(id: SessionId, title: String)
```

- [ ] **Step 9: Add delegation in `OpenRouterAgent`**

Add to `ai-agent/src/main/kotlin/com/ai/challenge/agent/OpenRouterAgent.kt`:

```kotlin
    override suspend fun updateSessionTitle(id: SessionId, title: String) = sessionRepository.updateTitle(id, title)
```

- [ ] **Step 10: Rewrite `SessionListStoreFactory.kt`** — use `Agent` instead of `sessionManager`

```kotlin
package com.ai.challenge.ui.sessionlist.store

import com.ai.challenge.core.Agent
import com.ai.challenge.core.SessionId
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class SessionListStoreFactory(
    private val storeFactory: StoreFactory,
    private val agent: Agent,
) {
    fun create(): SessionListStore =
        object : SessionListStore,
            Store<SessionListStore.Intent, SessionListStore.State, Nothing> by storeFactory.create(
                name = "SessionListStore",
                initialState = SessionListStore.State(),
                executorFactory = { ExecutorImpl(agent) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class SessionsLoaded(val sessions: List<SessionListStore.SessionItem>, val activeSessionId: SessionId?) : Msg
        data class SessionCreated(val item: SessionListStore.SessionItem) : Msg
        data class SessionDeleted(val id: SessionId, val newActiveId: SessionId?) : Msg
        data class SessionSelected(val id: SessionId) : Msg
    }

    private class ExecutorImpl(
        private val agent: Agent,
    ) : CoroutineExecutor<SessionListStore.Intent, Nothing, SessionListStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: SessionListStore.Intent) {
            when (intent) {
                is SessionListStore.Intent.LoadSessions -> handleLoadSessions()
                is SessionListStore.Intent.CreateSession -> handleCreateSession()
                is SessionListStore.Intent.DeleteSession -> handleDeleteSession(intent.id)
                is SessionListStore.Intent.SelectSession -> dispatch(Msg.SessionSelected(intent.id))
            }
        }

        private fun handleLoadSessions() {
            scope.launch {
                val sessions = agent.listSessions().map { session ->
                    SessionListStore.SessionItem(
                        id = session.id,
                        title = session.title,
                        updatedAt = session.updatedAt,
                    )
                }
                dispatch(Msg.SessionsLoaded(sessions, activeSessionId = state().activeSessionId))
            }
        }

        private fun handleCreateSession() {
            scope.launch {
                val id = agent.createSession()
                val session = agent.getSession(id)!!
                val item = SessionListStore.SessionItem(
                    id = session.id,
                    title = session.title,
                    updatedAt = session.updatedAt,
                )
                dispatch(Msg.SessionCreated(item))
            }
        }

        private fun handleDeleteSession(id: SessionId) {
            scope.launch {
                agent.deleteSession(id)
                val remaining = agent.listSessions()
                val currentActive = state().activeSessionId
                val newActiveId = if (currentActive == id) {
                    remaining.firstOrNull()?.id
                } else {
                    currentActive
                }
                dispatch(Msg.SessionDeleted(id, newActiveId))
            }
        }
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<SessionListStore.State, Msg> {
        override fun SessionListStore.State.reduce(msg: Msg): SessionListStore.State =
            when (msg) {
                is Msg.SessionsLoaded -> copy(
                    sessions = msg.sessions,
                    activeSessionId = msg.activeSessionId,
                )
                is Msg.SessionCreated -> copy(
                    sessions = listOf(msg.item) + sessions,
                    activeSessionId = msg.item.id,
                )
                is Msg.SessionDeleted -> copy(
                    sessions = sessions.filter { it.id != msg.id },
                    activeSessionId = msg.newActiveId,
                )
                is Msg.SessionSelected -> copy(
                    activeSessionId = msg.id,
                )
            }
    }
}
```

- [ ] **Step 11: Update `SessionListStore.kt`** — fix import

Replace `com.ai.challenge.session.SessionId` import with `com.ai.challenge.core.SessionId`. Also replace `kotlin.time.Instant` with the matching Instant type from core:

```kotlin
package com.ai.challenge.ui.sessionlist.store

import com.ai.challenge.core.SessionId
import com.arkivanov.mvikotlin.core.store.Store
import kotlinx.datetime.Instant

interface SessionListStore : Store<SessionListStore.Intent, SessionListStore.State, Nothing> {

    sealed interface Intent {
        data object LoadSessions : Intent
        data object CreateSession : Intent
        data class DeleteSession(val id: SessionId) : Intent
        data class SelectSession(val id: SessionId) : Intent
    }

    data class State(
        val sessions: List<SessionItem> = emptyList(),
        val activeSessionId: SessionId? = null,
    )

    data class SessionItem(
        val id: SessionId,
        val title: String,
        val updatedAt: Instant,
    )
}
```

Note: Match the `Instant` import to whichever `Instant` type `core` uses (`kotlin.time.Instant` or `kotlinx.datetime.Instant`).

- [ ] **Step 12: Update `UiMessage.kt`** — fix import

```kotlin
package com.ai.challenge.ui.model

import com.ai.challenge.core.TurnId

data class UiMessage(
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val turnId: TurnId? = null,
)
```

- [ ] **Step 13: Update `ChatContent.kt`** — replace `RequestMetrics` with `TokenDetails`/`CostDetails`

Key changes:
- `MessageBubble` receives `TokenDetails?` and `CostDetails?` instead of `RequestMetrics?`
- `SessionMetricsBar` receives `TokenDetails` and `CostDetails`
- `formatTurnMetrics` takes `TokenDetails` and `CostDetails`
- `formatSessionMetrics` takes `TokenDetails` and `CostDetails`
- Remove all `RequestMetrics` imports, add `TokenDetails`/`CostDetails` from `com.ai.challenge.core`

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
import com.ai.challenge.core.CostDetails
import com.ai.challenge.core.TokenDetails
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
                    val tokens = message.turnId?.let { state.turnTokens[it] }
                    val costs = message.turnId?.let { state.turnCosts[it] }
                    MessageBubble(message, tokens, costs)
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
            if (state.sessionTokens.totalTokens > 0) {
                SessionMetricsBar(state.sessionTokens, state.sessionCosts)
            }
    }
}

@Composable
private fun MessageBubble(message: UiMessage, tokens: TokenDetails?, costs: CostDetails?) {
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
            if (!message.isUser && tokens != null && costs != null && tokens.totalTokens > 0) {
                Text(
                    text = formatTurnMetrics(tokens, costs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SessionMetricsBar(tokens: TokenDetails, costs: CostDetails) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = formatSessionMetrics(tokens, costs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatCost(value: Double): String =
    String.format("%.10f", value).trimEnd('0').trimEnd('.')

private fun formatTurnMetrics(tokens: TokenDetails, costs: CostDetails): String {
    val parts = mutableListOf<String>()
    parts.add("\u2191${tokens.promptTokens}")
    parts.add("\u2193${tokens.completionTokens}")
    parts.add("cached:${tokens.cachedTokens}")
    if (tokens.reasoningTokens > 0) parts.add("reasoning:${tokens.reasoningTokens}")
    parts.addAll(formatCostParts(costs))
    return parts.joinToString("  ")
}

private fun formatSessionMetrics(tokens: TokenDetails, costs: CostDetails): String {
    val parts = mutableListOf<String>()
    parts.add("Session: \u2191${tokens.promptTokens}  \u2193${tokens.completionTokens}  cached:${tokens.cachedTokens}")
    parts.addAll(formatCostParts(costs))
    return parts.joinToString("  |  ")
}

private fun formatCostParts(cost: CostDetails): List<String> = buildList {
    add("cost:$${formatCost(cost.totalCost)}")
    if (cost.upstreamCost > 0 && cost.upstreamCost != cost.totalCost) add("upstream:$${formatCost(cost.upstreamCost)}")
    add("prompt:$${formatCost(cost.upstreamPromptCost)}")
    add("completion:$${formatCost(cost.upstreamCompletionsCost)}")
}
```

- [ ] **Step 14: Update `RootContent.kt`** — fix `SessionId` import

Change `com.ai.challenge.session.SessionId` to `com.ai.challenge.core.SessionId`:

```kotlin
    onSelectSession: (com.ai.challenge.core.SessionId) -> Unit,
    onDeleteSession: (com.ai.challenge.core.SessionId) -> Unit,
```

Or add the import at the top and use unqualified names. The simplest fix is to replace the import.

- [ ] **Step 15: Verify compilation**

Run: `./gradlew :compose-ui:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 16: Commit**

```bash
git add compose-ui/ core/ ai-agent/
git commit -m "feat: update compose-ui to use Agent as sole data source"
```

---

### Task 10: Update `compose-ui` tests

**Files:**
- Modify: `compose-ui/src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt`
- Modify: `compose-ui/src/test/kotlin/com/ai/challenge/ui/sessionlist/store/SessionListStoreTest.kt`

- [ ] **Step 1: Rewrite `ChatStoreTest.kt`**

Replace `InMemorySessionManager`/`InMemoryUsageManager` with a `FakeAgent` that implements the full `Agent` interface:

```kotlin
package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.core.Agent
import com.ai.challenge.core.AgentError
import com.ai.challenge.core.AgentResponse
import com.ai.challenge.core.AgentSession
import com.ai.challenge.core.CostDetails
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.TokenDetails
import com.ai.challenge.core.Turn
import com.ai.challenge.core.TurnId
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.concurrent.ConcurrentHashMap
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

    private fun createStore(agent: Agent = FakeAgent()): ChatStore =
        ChatStoreFactory(DefaultStoreFactory(), agent).create()

    @Test
    fun `initial state is empty with no session`() {
        val store = createStore()

        assertEquals(emptyList(), store.state.messages)
        assertFalse(store.state.isLoading)
        assertNull(store.state.sessionId)
        assertEquals(emptyMap(), store.state.turnTokens)
        assertEquals(emptyMap(), store.state.turnCosts)
        assertEquals(TokenDetails(), store.state.sessionTokens)
        assertEquals(CostDetails(), store.state.sessionCosts)

        store.dispose()
    }

    @Test
    fun `LoadSession sets sessionId and loads history as UiMessages`() = runTest {
        val agent = FakeAgent()
        val sessionId = agent.createSession()
        val turn = Turn(userMessage = "hi", agentResponse = "hello")
        agent.appendTurnDirect(sessionId, turn)
        val store = createStore(agent)

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
        val turnId = TurnId.generate()
        val agent = FakeAgent(
            sendResult = Either.Right(AgentResponse("Hello from agent!", turnId, TokenDetails(), CostDetails()))
        )
        val sessionId = agent.createSession()
        val store = createStore(agent)

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
        val agent = FakeAgent(sendResult = Either.Left(AgentError.NetworkError("Timeout")))
        val sessionId = agent.createSession()
        val store = createStore(agent)

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
    fun `SendMessage populates turnTokens, turnCosts, sessionTokens, sessionCosts`() = runTest {
        val turnId = TurnId.generate()
        val tokens = TokenDetails(promptTokens = 100, completionTokens = 50)
        val costs = CostDetails(totalCost = 0.001)
        val agent = FakeAgent(
            sendResult = Either.Right(AgentResponse(text = "Hi!", turnId = turnId, tokenDetails = tokens, costDetails = costs))
        )
        val sessionId = agent.createSession()
        val store = createStore(agent)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hello"))
        advanceUntilIdle()

        assertEquals(tokens, store.state.turnTokens[turnId])
        assertEquals(costs, store.state.turnCosts[turnId])
        assertEquals(tokens, store.state.sessionTokens)
        assertEquals(costs, store.state.sessionCosts)

        store.dispose()
    }

    @Test
    fun `SendMessage accumulates session metrics across multiple turns`() = runTest {
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        val tokens1 = TokenDetails(promptTokens = 100, completionTokens = 50)
        val costs1 = CostDetails(totalCost = 0.001)
        val tokens2 = TokenDetails(promptTokens = 200, completionTokens = 100)
        val costs2 = CostDetails(totalCost = 0.002)

        var callCount = 0
        val agent = object : FakeAgent() {
            override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> {
                callCount++
                return if (callCount == 1) {
                    Either.Right(AgentResponse("r1", turnId1, tokens1, costs1))
                } else {
                    Either.Right(AgentResponse("r2", turnId2, tokens2, costs2))
                }
            }
        }
        val sessionId = agent.createSession()
        val store = createStore(agent)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hello"))
        advanceUntilIdle()
        store.accept(ChatStore.Intent.SendMessage("Again"))
        advanceUntilIdle()

        assertEquals(tokens1 + tokens2, store.state.sessionTokens)
        assertEquals(costs1 + costs2, store.state.sessionCosts)
        assertEquals(2, store.state.turnTokens.size)

        store.dispose()
    }

    @Test
    fun `LoadSession loads token and cost data from agent`() = runTest {
        val agent = FakeAgent()
        val sessionId = agent.createSession()

        val turn1 = Turn(userMessage = "a", agentResponse = "b")
        val turn2 = Turn(userMessage = "c", agentResponse = "d")
        val turnId1 = agent.appendTurnDirect(sessionId, turn1)
        val turnId2 = agent.appendTurnDirect(sessionId, turn2)
        val t1 = TokenDetails(promptTokens = 10, completionTokens = 5)
        val t2 = TokenDetails(promptTokens = 20, completionTokens = 10)
        val c1 = CostDetails(totalCost = 0.001)
        val c2 = CostDetails(totalCost = 0.002)
        agent.recordTokensDirect(sessionId, turnId1, t1)
        agent.recordTokensDirect(sessionId, turnId2, t2)
        agent.recordCostsDirect(sessionId, turnId1, c1)
        agent.recordCostsDirect(sessionId, turnId2, c2)

        val store = createStore(agent)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        assertEquals(t1, store.state.turnTokens[turnId1])
        assertEquals(t2, store.state.turnTokens[turnId2])
        assertEquals(t1 + t2, store.state.sessionTokens)
        assertEquals(c1 + c2, store.state.sessionCosts)

        store.dispose()
    }

    @Test
    fun `SendMessage auto-titles session on first message`() = runTest {
        val agent = FakeAgent(
            sendResult = Either.Right(AgentResponse("response", TurnId.generate(), TokenDetails(), CostDetails()))
        )
        val sessionId = agent.createSession()
        val store = createStore(agent)

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hello world, this is a long message for auto-title testing purposes"))
        advanceUntilIdle()

        val title = agent.getSession(sessionId)?.title ?: ""
        assertEquals("Hello world, this is a long message for auto-title", title)

        store.dispose()
    }
}

open class FakeAgent(
    private val sendResult: Either<AgentError, AgentResponse> = Either.Right(AgentResponse("", TurnId.generate(), TokenDetails(), CostDetails())),
) : Agent {
    private val sessions = ConcurrentHashMap<SessionId, AgentSession>()
    private val turns = ConcurrentHashMap<TurnId, Pair<SessionId, Turn>>()
    private val tokenData = ConcurrentHashMap<TurnId, Pair<SessionId, TokenDetails>>()
    private val costData = ConcurrentHashMap<TurnId, Pair<SessionId, CostDetails>>()

    override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> = sendResult
    override suspend fun createSession(title: String): SessionId {
        val id = SessionId.generate()
        sessions[id] = AgentSession(id = id, title = title)
        return id
    }
    override suspend fun deleteSession(id: SessionId): Boolean = sessions.remove(id) != null
    override suspend fun listSessions(): List<AgentSession> = sessions.values.toList()
    override suspend fun getSession(id: SessionId): AgentSession? = sessions[id]
    override suspend fun updateSessionTitle(id: SessionId, title: String) {
        sessions.computeIfPresent(id) { _, s -> s.copy(title = title) }
    }
    override suspend fun getTurns(sessionId: SessionId, limit: Int?): List<Turn> {
        val all = turns.values.filter { it.first == sessionId }.map { it.second }.sortedBy { it.timestamp }
        return if (limit != null && all.size > limit) all.takeLast(limit) else all
    }
    override suspend fun getTokensByTurn(turnId: TurnId): TokenDetails? = tokenData[turnId]?.second
    override suspend fun getTokensBySession(sessionId: SessionId): Map<TurnId, TokenDetails> =
        tokenData.filter { it.value.first == sessionId }.mapValues { it.value.second }
    override suspend fun getSessionTotalTokens(sessionId: SessionId): TokenDetails =
        getTokensBySession(sessionId).values.fold(TokenDetails()) { acc, t -> acc + t }
    override suspend fun getCostByTurn(turnId: TurnId): CostDetails? = costData[turnId]?.second
    override suspend fun getCostBySession(sessionId: SessionId): Map<TurnId, CostDetails> =
        costData.filter { it.value.first == sessionId }.mapValues { it.value.second }
    override suspend fun getSessionTotalCost(sessionId: SessionId): CostDetails =
        getCostBySession(sessionId).values.fold(CostDetails()) { acc, c -> acc + c }

    // Test helpers
    fun appendTurnDirect(sessionId: SessionId, turn: Turn): TurnId {
        turns[turn.id] = sessionId to turn
        return turn.id
    }
    fun recordTokensDirect(sessionId: SessionId, turnId: TurnId, details: TokenDetails) {
        tokenData[turnId] = sessionId to details
    }
    fun recordCostsDirect(sessionId: SessionId, turnId: TurnId, details: CostDetails) {
        costData[turnId] = sessionId to details
    }
}
```

- [ ] **Step 2: Rewrite `SessionListStoreTest.kt`**

```kotlin
package com.ai.challenge.ui.sessionlist.store

import com.ai.challenge.ui.chat.store.FakeAgent
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionListStoreTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty session list`() {
        val agent = FakeAgent()
        val store = SessionListStoreFactory(DefaultStoreFactory(), agent).create()

        assertTrue(store.state.sessions.isEmpty())
        assertNull(store.state.activeSessionId)

        store.dispose()
    }

    @Test
    fun `LoadSessions populates session list`() = runTest {
        val agent = FakeAgent()
        agent.createSession(title = "Chat 1")
        agent.createSession(title = "Chat 2")

        val store = SessionListStoreFactory(DefaultStoreFactory(), agent).create()
        store.accept(SessionListStore.Intent.LoadSessions)
        advanceUntilIdle()

        assertEquals(2, store.state.sessions.size)

        store.dispose()
    }

    @Test
    fun `CreateSession creates new session and makes it active`() = runTest {
        val agent = FakeAgent()
        val store = SessionListStoreFactory(DefaultStoreFactory(), agent).create()

        store.accept(SessionListStore.Intent.CreateSession)
        advanceUntilIdle()

        assertEquals(1, store.state.sessions.size)
        assertNotNull(store.state.activeSessionId)
        assertEquals(store.state.sessions[0].id, store.state.activeSessionId)

        store.dispose()
    }

    @Test
    fun `DeleteSession removes session from list`() = runTest {
        val agent = FakeAgent()
        val id = agent.createSession(title = "To delete")

        val store = SessionListStoreFactory(DefaultStoreFactory(), agent).create()
        store.accept(SessionListStore.Intent.LoadSessions)
        advanceUntilIdle()

        store.accept(SessionListStore.Intent.DeleteSession(id))
        advanceUntilIdle()

        assertTrue(store.state.sessions.isEmpty())
        assertNull(agent.getSession(id))

        store.dispose()
    }

    @Test
    fun `DeleteSession switches active to first remaining if active was deleted`() = runTest {
        val agent = FakeAgent()
        val id1 = agent.createSession(title = "First")
        val id2 = agent.createSession(title = "Second")

        val store = SessionListStoreFactory(DefaultStoreFactory(), agent).create()
        store.accept(SessionListStore.Intent.LoadSessions)
        advanceUntilIdle()

        store.accept(SessionListStore.Intent.SelectSession(id1))
        advanceUntilIdle()

        store.accept(SessionListStore.Intent.DeleteSession(id1))
        advanceUntilIdle()

        assertEquals(1, store.state.sessions.size)
        assertEquals(id2, store.state.activeSessionId)

        store.dispose()
    }

    @Test
    fun `SelectSession sets activeSessionId`() = runTest {
        val agent = FakeAgent()
        val id = agent.createSession(title = "Test")

        val store = SessionListStoreFactory(DefaultStoreFactory(), agent).create()
        store.accept(SessionListStore.Intent.LoadSessions)
        advanceUntilIdle()

        store.accept(SessionListStore.Intent.SelectSession(id))
        advanceUntilIdle()

        assertEquals(id, store.state.activeSessionId)

        store.dispose()
    }
}
```

Note: `FakeAgent` is imported from `com.ai.challenge.ui.chat.store.FakeAgent`. If visibility is an issue (it's currently `open class`), ensure `FakeAgent` is public or move it to a shared test-fixtures location. Alternatively, duplicate it in this test file.

- [ ] **Step 3: Run all compose-ui tests**

Run: `./gradlew :compose-ui:test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 4: Commit**

```bash
git add compose-ui/
git commit -m "feat: update compose-ui tests for new Agent-only architecture"
```

---

### Task 11: Delete `session-storage` module

**Files:**
- Delete: `session-storage/` (entire directory)
- Modify: `settings.gradle.kts` — remove `include("session-storage")`

- [ ] **Step 1: Remove `include("session-storage")` from `settings.gradle.kts`**

- [ ] **Step 2: Delete `session-storage/` directory**

```bash
rm -rf session-storage/
```

- [ ] **Step 3: Run full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. All modules compile, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: delete session-storage module"
```

---

### Task 12: Final verification

- [ ] **Step 1: Run all tests**

Run: `./gradlew test`
Expected: All tests pass across all modules

- [ ] **Step 2: Verify no references to old packages remain**

```bash
grep -r "com.ai.challenge.session" --include="*.kt" . | grep -v "session/repository" | grep -v "session-storage" | grep -v build/
```

Expected: No results (all old `com.ai.challenge.session` imports replaced with `com.ai.challenge.core` or new repository packages)

- [ ] **Step 3: Verify no references to `RequestMetrics` remain**

```bash
grep -r "RequestMetrics" --include="*.kt" . | grep -v build/
```

Expected: No results

- [ ] **Step 4: Verify no references to `InMemory` implementations remain**

```bash
grep -r "InMemory" --include="*.kt" . | grep -v build/
```

Expected: No results

- [ ] **Step 5: Final commit (if any fixes needed)**

```bash
git add -A
git commit -m "chore: clean up remaining references to old architecture"
```
