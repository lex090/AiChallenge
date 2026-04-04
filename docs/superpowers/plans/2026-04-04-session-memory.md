# Session Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Agent persists chat history in SQLite and restores it on restart. Users manage multiple sessions via a drawer UI.

**Architecture:** New `session-storage` module holds domain models, interface, and SQLite implementation (Exposed). `ai-agent` depends on `session-storage` for `AgentSessionManager`. Agent reads history before each LLM call and saves turns on success. `compose-ui` adds `SessionListStore` for drawer and updates `ChatStore` to work with session IDs.

**Tech Stack:** Exposed 0.61.0 (Core + JDBC), SQLite JDBC 3.49.1.0, kotlinx-datetime 0.6.2

**Spec:** `docs/superpowers/specs/2026-04-04-session-memory-design.md`

---

## File Structure

### New module: `session-storage`

| File | Responsibility |
|------|---------------|
| `session-storage/build.gradle.kts` | Module build config: Exposed, SQLite JDBC, kotlinx-datetime |
| `session-storage/src/main/kotlin/com/ai/challenge/session/SessionId.kt` | Value class for session ID |
| `session-storage/src/main/kotlin/com/ai/challenge/session/Turn.kt` | Data class for one Q&A pair |
| `session-storage/src/main/kotlin/com/ai/challenge/session/AgentSession.kt` | Data class for full session with history |
| `session-storage/src/main/kotlin/com/ai/challenge/session/AgentSessionManager.kt` | Interface: CRUD + history operations |
| `session-storage/src/main/kotlin/com/ai/challenge/session/InMemorySessionManager.kt` | ConcurrentHashMap impl for tests |
| `session-storage/src/main/kotlin/com/ai/challenge/session/ExposedSessionManager.kt` | SQLite impl via Exposed (includes table definitions) |
| `session-storage/src/main/kotlin/com/ai/challenge/session/DatabaseFactory.kt` | `createSessionDatabase()` helper |
| `session-storage/src/test/kotlin/com/ai/challenge/session/InMemorySessionManagerTest.kt` | Tests for InMemorySessionManager |
| `session-storage/src/test/kotlin/com/ai/challenge/session/ExposedSessionManagerTest.kt` | Tests for ExposedSessionManager with in-memory SQLite |

### Modified files

| File | Change |
|------|--------|
| `settings.gradle.kts` | Add `include("session-storage")` |
| `gradle/libs.versions.toml` | Add exposed, sqlite-jdbc, kotlinx-datetime versions + libraries |
| `ai-agent/build.gradle.kts` | Add `implementation(project(":session-storage"))` |
| `ai-agent/src/main/kotlin/com/ai/challenge/agent/Agent.kt` | Add `sessionId` param to `send()` |
| `ai-agent/src/main/kotlin/com/ai/challenge/agent/OpenRouterAgent.kt` | Add `sessionManager` field, read history, save turns |
| `ai-agent/src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt` | Update all tests for new signature, add history tests |
| `compose-ui/build.gradle.kts` | Add `implementation(project(":session-storage"))` |
| `compose-ui/src/main/kotlin/com/ai/challenge/ui/di/AppModule.kt` | Add Database, AgentSessionManager, update Agent binding |
| `compose-ui/src/main/kotlin/com/ai/challenge/ui/main.kt` | Pass `AgentSessionManager` to RootComponent |
| `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt` | Add `LoadSession` intent, `sessionId` to State |
| `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt` | Add sessionManager, handle LoadSession, auto-title |
| `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatComponent.kt` | Accept `sessionId`, fire `LoadSession` on init |
| `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt` | No changes (renders from state as before) |
| `compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootComponent.kt` | Add SessionListStore, session selection, drawer state |
| `compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootContent.kt` | Wrap in ModalNavigationDrawer |
| `compose-ui/src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt` | Update FakeAgent, add session-aware tests |

### New files in `compose-ui`

| File | Responsibility |
|------|---------------|
| `compose-ui/src/main/kotlin/com/ai/challenge/ui/sessionlist/store/SessionListStore.kt` | Store interface: Intent, State, SessionItem |
| `compose-ui/src/main/kotlin/com/ai/challenge/ui/sessionlist/store/SessionListStoreFactory.kt` | Store impl: executor + reducer |
| `compose-ui/src/test/kotlin/com/ai/challenge/ui/sessionlist/store/SessionListStoreTest.kt` | Tests for SessionListStore |

---

### Task 1: Build configuration — Gradle catalog, new module, settings

**Files:**
- Modify: `gradle/libs.versions.toml`
- Create: `session-storage/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `ai-agent/build.gradle.kts`
- Modify: `compose-ui/build.gradle.kts`

- [ ] **Step 1: Add new dependencies to version catalog**

In `gradle/libs.versions.toml`, add versions and libraries:

```toml
# Add to [versions] section, after the slf4j line:
exposed = "0.61.0"
sqlite-jdbc = "3.49.1.0"
kotlinx-datetime = "0.6.2"

# Add to [libraries] section, after the slf4j-nop line:

# Exposed (SQL framework)
exposed-core = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
exposed-jdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }

# SQLite
sqlite-jdbc = { module = "org.xerial:sqlite-jdbc", version.ref = "sqlite-jdbc" }

# Kotlinx Datetime
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
```

- [ ] **Step 2: Create `session-storage/build.gradle.kts`**

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

- [ ] **Step 3: Register module in `settings.gradle.kts`**

Add after `include("ai-agent")`:

```kotlin
include("session-storage")
```

Full file becomes:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "AiChallenge"

include("llm-service")
include("ai-agent")
include("session-storage")
include("compose-ui")
include("week1")
```

- [ ] **Step 4: Add session-storage dependency to `ai-agent/build.gradle.kts`**

Add to dependencies block:

```kotlin
implementation(project(":session-storage"))
```

- [ ] **Step 5: Add session-storage dependency to `compose-ui/build.gradle.kts`**

Add to dependencies block:

```kotlin
implementation(project(":session-storage"))
```

- [ ] **Step 6: Verify Gradle sync**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :session-storage:dependencies --configuration compileClasspath`

Expected: BUILD SUCCESSFUL, shows exposed-core, exposed-jdbc, sqlite-jdbc, kotlinx-datetime

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts session-storage/build.gradle.kts ai-agent/build.gradle.kts compose-ui/build.gradle.kts
git commit -m "feat: add session-storage module with build configuration"
```

---

### Task 2: Domain models — SessionId, Turn, AgentSession

**Files:**
- Create: `session-storage/src/main/kotlin/com/ai/challenge/session/SessionId.kt`
- Create: `session-storage/src/main/kotlin/com/ai/challenge/session/Turn.kt`
- Create: `session-storage/src/main/kotlin/com/ai/challenge/session/AgentSession.kt`

- [ ] **Step 1: Create `SessionId.kt`**

```kotlin
package com.ai.challenge.session

import java.util.UUID

@JvmInline
value class SessionId(val value: String) {
    companion object {
        fun generate(): SessionId = SessionId(UUID.randomUUID().toString())
    }
}
```

- [ ] **Step 2: Create `Turn.kt`**

```kotlin
package com.ai.challenge.session

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class Turn(
    val userMessage: String,
    val agentResponse: String,
    val timestamp: Instant = Clock.System.now(),
)
```

- [ ] **Step 3: Create `AgentSession.kt`**

```kotlin
package com.ai.challenge.session

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class AgentSession(
    val id: SessionId,
    val title: String = "",
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val history: List<Turn> = emptyList(),
) {
    fun addTurn(turn: Turn): AgentSession =
        copy(history = history + turn, updatedAt = Clock.System.now())
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :session-storage:compileKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add session-storage/src/main/kotlin/com/ai/challenge/session/
git commit -m "feat: add session domain models — SessionId, Turn, AgentSession"
```

---

### Task 3: AgentSessionManager interface + InMemorySessionManager

**Files:**
- Create: `session-storage/src/main/kotlin/com/ai/challenge/session/AgentSessionManager.kt`
- Create: `session-storage/src/main/kotlin/com/ai/challenge/session/InMemorySessionManager.kt`
- Create: `session-storage/src/test/kotlin/com/ai/challenge/session/InMemorySessionManagerTest.kt`

- [ ] **Step 1: Write the tests first**

Create `session-storage/src/test/kotlin/com/ai/challenge/session/InMemorySessionManagerTest.kt`:

```kotlin
package com.ai.challenge.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class InMemorySessionManagerTest {

    private val manager = InMemorySessionManager()

    @Test
    fun `createSession returns unique session id`() {
        val id1 = manager.createSession()
        val id2 = manager.createSession()
        assertTrue(id1 != id2)
    }

    @Test
    fun `getSession returns created session`() {
        val id = manager.createSession(title = "Test chat")
        val session = manager.getSession(id)
        assertNotNull(session)
        assertEquals("Test chat", session.title)
        assertEquals(id, session.id)
        assertTrue(session.history.isEmpty())
    }

    @Test
    fun `getSession returns null for unknown id`() {
        assertNull(manager.getSession(SessionId("nonexistent")))
    }

    @Test
    fun `deleteSession removes session and returns true`() {
        val id = manager.createSession()
        assertTrue(manager.deleteSession(id))
        assertNull(manager.getSession(id))
    }

    @Test
    fun `deleteSession returns false for unknown id`() {
        assertFalse(manager.deleteSession(SessionId("nonexistent")))
    }

    @Test
    fun `listSessions returns all sessions sorted by updatedAt descending`() {
        val id1 = manager.createSession(title = "First")
        val id2 = manager.createSession(title = "Second")
        // Append turn to id1 to make its updatedAt newer
        manager.appendTurn(id1, Turn(userMessage = "hi", agentResponse = "hello"))

        val sessions = manager.listSessions()
        assertEquals(2, sessions.size)
        assertEquals(id1, sessions[0].id) // id1 updated more recently
        assertEquals(id2, sessions[1].id)
    }

    @Test
    fun `appendTurn adds turn to session history`() {
        val id = manager.createSession()
        val turn = Turn(userMessage = "hi", agentResponse = "hello")
        manager.appendTurn(id, turn)

        val history = manager.getHistory(id)
        assertEquals(1, history.size)
        assertEquals("hi", history[0].userMessage)
        assertEquals("hello", history[0].agentResponse)
    }

    @Test
    fun `getHistory with limit returns last N turns`() {
        val id = manager.createSession()
        manager.appendTurn(id, Turn(userMessage = "1", agentResponse = "a"))
        manager.appendTurn(id, Turn(userMessage = "2", agentResponse = "b"))
        manager.appendTurn(id, Turn(userMessage = "3", agentResponse = "c"))

        val history = manager.getHistory(id, limit = 2)
        assertEquals(2, history.size)
        assertEquals("2", history[0].userMessage)
        assertEquals("3", history[1].userMessage)
    }

    @Test
    fun `getHistory returns empty list for unknown session`() {
        assertTrue(manager.getHistory(SessionId("nonexistent")).isEmpty())
    }

    @Test
    fun `updateTitle changes session title`() {
        val id = manager.createSession(title = "Old")
        manager.updateTitle(id, "New")
        assertEquals("New", manager.getSession(id)?.title)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :session-storage:test`

Expected: Compilation error — `AgentSessionManager` and `InMemorySessionManager` don't exist yet.

- [ ] **Step 3: Create `AgentSessionManager.kt`**

```kotlin
package com.ai.challenge.session

interface AgentSessionManager {
    fun createSession(title: String = ""): SessionId
    fun getSession(id: SessionId): AgentSession?
    fun deleteSession(id: SessionId): Boolean
    fun listSessions(): List<AgentSession>
    fun getHistory(id: SessionId, limit: Int? = null): List<Turn>
    fun appendTurn(id: SessionId, turn: Turn)
    fun updateTitle(id: SessionId, title: String)
}
```

- [ ] **Step 4: Create `InMemorySessionManager.kt`**

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

    override fun appendTurn(id: SessionId, turn: Turn) {
        sessions.computeIfPresent(id) { _, session -> session.addTurn(turn) }
    }

    override fun updateTitle(id: SessionId, title: String) {
        sessions.computeIfPresent(id) { _, session ->
            session.copy(title = title, updatedAt = kotlinx.datetime.Clock.System.now())
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :session-storage:test`

Expected: All 9 tests PASS

- [ ] **Step 6: Commit**

```bash
git add session-storage/src/
git commit -m "feat: add AgentSessionManager interface and InMemorySessionManager"
```

---

### Task 4: ExposedSessionManager — SQLite persistence

**Files:**
- Create: `session-storage/src/main/kotlin/com/ai/challenge/session/ExposedSessionManager.kt`
- Create: `session-storage/src/main/kotlin/com/ai/challenge/session/DatabaseFactory.kt`
- Create: `session-storage/src/test/kotlin/com/ai/challenge/session/ExposedSessionManagerTest.kt`

- [ ] **Step 1: Write the tests first**

Create `session-storage/src/test/kotlin/com/ai/challenge/session/ExposedSessionManagerTest.kt`:

```kotlin
package com.ai.challenge.session

import org.jetbrains.exposed.sql.Database
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ExposedSessionManagerTest {

    private lateinit var db: Database
    private lateinit var manager: ExposedSessionManager

    @BeforeTest
    fun setUp() {
        db = Database.connect("jdbc:sqlite::memory:", driver = "org.sqlite.JDBC")
        manager = ExposedSessionManager(db)
    }

    @Test
    fun `createSession and getSession round-trip`() {
        val id = manager.createSession(title = "Test chat")
        val session = manager.getSession(id)
        assertNotNull(session)
        assertEquals("Test chat", session.title)
        assertEquals(id, session.id)
        assertTrue(session.history.isEmpty())
    }

    @Test
    fun `getSession returns null for unknown id`() {
        assertNull(manager.getSession(SessionId("nonexistent")))
    }

    @Test
    fun `deleteSession removes session and returns true`() {
        val id = manager.createSession()
        assertTrue(manager.deleteSession(id))
        assertNull(manager.getSession(id))
    }

    @Test
    fun `deleteSession returns false for unknown id`() {
        assertFalse(manager.deleteSession(SessionId("nonexistent")))
    }

    @Test
    fun `deleteSession cascades to turns`() {
        val id = manager.createSession()
        manager.appendTurn(id, Turn(userMessage = "hi", agentResponse = "hello"))
        manager.deleteSession(id)
        assertTrue(manager.getHistory(id).isEmpty())
    }

    @Test
    fun `listSessions returns all sessions sorted by updatedAt descending`() {
        val id1 = manager.createSession(title = "First")
        val id2 = manager.createSession(title = "Second")
        manager.appendTurn(id1, Turn(userMessage = "hi", agentResponse = "hello"))

        val sessions = manager.listSessions()
        assertEquals(2, sessions.size)
        assertEquals(id1, sessions[0].id)
        assertEquals(id2, sessions[1].id)
    }

    @Test
    fun `listSessions returns sessions without history loaded`() {
        val id = manager.createSession()
        manager.appendTurn(id, Turn(userMessage = "hi", agentResponse = "hello"))

        val sessions = manager.listSessions()
        assertTrue(sessions[0].history.isEmpty())
    }

    @Test
    fun `appendTurn and getHistory round-trip`() {
        val id = manager.createSession()
        manager.appendTurn(id, Turn(userMessage = "hi", agentResponse = "hello"))
        manager.appendTurn(id, Turn(userMessage = "how", agentResponse = "fine"))

        val history = manager.getHistory(id)
        assertEquals(2, history.size)
        assertEquals("hi", history[0].userMessage)
        assertEquals("hello", history[0].agentResponse)
        assertEquals("how", history[1].userMessage)
        assertEquals("fine", history[1].agentResponse)
    }

    @Test
    fun `getHistory with limit returns last N turns`() {
        val id = manager.createSession()
        manager.appendTurn(id, Turn(userMessage = "1", agentResponse = "a"))
        manager.appendTurn(id, Turn(userMessage = "2", agentResponse = "b"))
        manager.appendTurn(id, Turn(userMessage = "3", agentResponse = "c"))

        val history = manager.getHistory(id, limit = 2)
        assertEquals(2, history.size)
        assertEquals("2", history[0].userMessage)
        assertEquals("3", history[1].userMessage)
    }

    @Test
    fun `getHistory returns empty list for unknown session`() {
        assertTrue(manager.getHistory(SessionId("nonexistent")).isEmpty())
    }

    @Test
    fun `updateTitle changes session title`() {
        val id = manager.createSession(title = "Old")
        manager.updateTitle(id, "New")
        assertEquals("New", manager.getSession(id)?.title)
    }

    @Test
    fun `getSession loads full history`() {
        val id = manager.createSession()
        manager.appendTurn(id, Turn(userMessage = "hi", agentResponse = "hello"))

        val session = manager.getSession(id)
        assertNotNull(session)
        assertEquals(1, session.history.size)
        assertEquals("hi", session.history[0].userMessage)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :session-storage:test --tests "com.ai.challenge.session.ExposedSessionManagerTest"`

Expected: Compilation error — `ExposedSessionManager` doesn't exist yet.

- [ ] **Step 3: Create `ExposedSessionManager.kt`**

This file contains the Exposed table definitions and the manager implementation:

```kotlin
package com.ai.challenge.session

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
    val id = integer("id").autoIncrement()
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
            SchemaUtils.create(SessionsTable, TurnsTable)
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
                    history = emptyList(), // intentionally empty for list view
                )
            }
    }

    override fun getHistory(id: SessionId, limit: Int?): List<Turn> = transaction(database) {
        loadHistory(id, limit)
    }

    override fun appendTurn(id: SessionId, turn: Turn) {
        val now = Clock.System.now()
        transaction(database) {
            TurnsTable.insert {
                it[sessionId] = id.value
                it[userMessage] = turn.userMessage
                it[agentResponse] = turn.agentResponse
                it[timestamp] = turn.timestamp.toEpochMilliseconds()
            }
            SessionsTable.update({ SessionsTable.id eq id.value }) {
                it[updatedAt] = now.toEpochMilliseconds()
            }
        }
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
            // Get total count, then offset to get last N
            val allRows = query.toList()
            if (allRows.size > limit) allRows.takeLast(limit) else allRows
        } else {
            query.toList()
        }

        return rows.map { row ->
            Turn(
                userMessage = row[TurnsTable.userMessage],
                agentResponse = row[TurnsTable.agentResponse],
                timestamp = Instant.fromEpochMilliseconds(row[TurnsTable.timestamp]),
            )
        }
    }
}
```

- [ ] **Step 4: Create `DatabaseFactory.kt`**

```kotlin
package com.ai.challenge.session

import org.jetbrains.exposed.sql.Database
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

fun createSessionDatabase(): Database {
    val dbDir = Path(System.getProperty("user.home"), ".ai-challenge")
    dbDir.createDirectories()
    val dbPath = dbDir.resolve("sessions.db")
    return Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :session-storage:test`

Expected: All tests PASS (both InMemorySessionManagerTest and ExposedSessionManagerTest)

- [ ] **Step 6: Commit**

```bash
git add session-storage/src/
git commit -m "feat: add ExposedSessionManager with SQLite persistence"
```

---

### Task 5: Update Agent interface and OpenRouterAgent

**Files:**
- Modify: `ai-agent/src/main/kotlin/com/ai/challenge/agent/Agent.kt`
- Modify: `ai-agent/src/main/kotlin/com/ai/challenge/agent/OpenRouterAgent.kt`
- Modify: `ai-agent/src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt`

- [ ] **Step 1: Write the updated tests first**

Replace the full content of `ai-agent/src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt`:

```kotlin
package com.ai.challenge.agent

import arrow.core.Either
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.session.InMemorySessionManager
import com.ai.challenge.session.SessionId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenRouterAgentTest {

    private val sessionManager = InMemorySessionManager()

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

    @Test
    fun `send returns Right with response text on success`() = runTest {
        val service = createService("""
            {"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}
        """.trimIndent())
        val agent = OpenRouterAgent(service, model = "test-model", sessionManager = sessionManager)
        val sessionId = sessionManager.createSession()

        val result = agent.send(sessionId, "Hi")

        assertIs<Either.Right<String>>(result)
        assertEquals("Hello!", result.value)
    }

    @Test
    fun `send saves turn to session on success`() = runTest {
        val service = createService("""
            {"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}
        """.trimIndent())
        val agent = OpenRouterAgent(service, model = "test-model", sessionManager = sessionManager)
        val sessionId = sessionManager.createSession()

        agent.send(sessionId, "Hi")

        val history = sessionManager.getHistory(sessionId)
        assertEquals(1, history.size)
        assertEquals("Hi", history[0].userMessage)
        assertEquals("Hello!", history[0].agentResponse)
    }

    @Test
    fun `send does not save turn on failure`() = runTest {
        val service = createService("""
            {"error":{"message":"Rate limit exceeded","code":429},"choices":[]}
        """.trimIndent())
        val agent = OpenRouterAgent(service, model = "test-model", sessionManager = sessionManager)
        val sessionId = sessionManager.createSession()

        agent.send(sessionId, "Hi")

        val history = sessionManager.getHistory(sessionId)
        assertTrue(history.isEmpty())
    }

    @Test
    fun `send returns Left ApiError when response has error`() = runTest {
        val service = createService("""
            {"error":{"message":"Rate limit exceeded","code":429},"choices":[]}
        """.trimIndent())
        val agent = OpenRouterAgent(service, model = "test-model", sessionManager = sessionManager)
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
        val agent = OpenRouterAgent(service, model = "test-model", sessionManager = sessionManager)
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
        val agent = OpenRouterAgent(service, model = "test-model", sessionManager = sessionManager)
        val sessionId = sessionManager.createSession()

        // First turn: populate history
        sessionManager.appendTurn(
            sessionId,
            com.ai.challenge.session.Turn(userMessage = "Hi", agentResponse = "Hello!"),
        )

        // Second call should include history
        agent.send(sessionId, "Remember me?")

        val json = Json.parseToJsonElement(capturedBody!!).jsonObject
        val messages = json["messages"]!!.jsonArray
        // Expected: user("Hi"), assistant("Hello!"), user("Remember me?")
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

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :ai-agent:test`

Expected: Compilation error — `Agent.send()` signature mismatch.

- [ ] **Step 3: Update `Agent.kt`**

Replace full content of `ai-agent/src/main/kotlin/com/ai/challenge/agent/Agent.kt`:

```kotlin
package com.ai.challenge.agent

import arrow.core.Either
import com.ai.challenge.session.SessionId

interface Agent {
    suspend fun send(sessionId: SessionId, message: String): Either<AgentError, String>
}
```

- [ ] **Step 4: Update `OpenRouterAgent.kt`**

Replace full content of `ai-agent/src/main/kotlin/com/ai/challenge/agent/OpenRouterAgent.kt`:

```kotlin
package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.Turn

class OpenRouterAgent(
    private val service: OpenRouterService,
    private val model: String,
    private val sessionManager: AgentSessionManager,
) : Agent {

    override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, String> = either {
        val history = sessionManager.getHistory(sessionId)

        val response = catch({
            service.chatText(model = model) {
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

        sessionManager.appendTurn(sessionId, Turn(userMessage = message, agentResponse = response))

        response
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :ai-agent:test`

Expected: All 6 tests PASS

- [ ] **Step 6: Commit**

```bash
git add ai-agent/src/
git commit -m "feat: update Agent interface to accept sessionId, add history to LLM calls"
```

---

### Task 6: Update ChatStore for session awareness

**Files:**
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt`
- Modify: `compose-ui/src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt`

- [ ] **Step 1: Write the updated tests first**

Replace full content of `compose-ui/src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt`:

```kotlin
package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.agent.Agent
import com.ai.challenge.agent.AgentError
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.InMemorySessionManager
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.Turn
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

    @Test
    fun `initial state is empty with no session`() {
        val sessionManager = InMemorySessionManager()
        val store = ChatStoreFactory(DefaultStoreFactory(), FakeAgent(), sessionManager).create()

        assertEquals(emptyList(), store.state.messages)
        assertFalse(store.state.isLoading)
        assertNull(store.state.sessionId)

        store.dispose()
    }

    @Test
    fun `LoadSession sets sessionId and loads history as UiMessages`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        sessionManager.appendTurn(sessionId, Turn(userMessage = "hi", agentResponse = "hello"))

        val store = ChatStoreFactory(DefaultStoreFactory(), FakeAgent(), sessionManager).create()

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        assertEquals(sessionId, store.state.sessionId)
        assertEquals(2, store.state.messages.size)
        assertEquals(UiMessage("hi", isUser = true), store.state.messages[0])
        assertEquals(UiMessage("hello", isUser = false), store.state.messages[1])

        store.dispose()
    }

    @Test
    fun `SendMessage adds user message and agent response`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val agent = FakeAgent(response = Either.Right("Hello from agent!"))
        val store = ChatStoreFactory(DefaultStoreFactory(), agent, sessionManager).create()

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hi"))
        advanceUntilIdle()

        val messages = store.state.messages
        assertEquals(2, messages.size)
        assertEquals(UiMessage("Hi", isUser = true), messages[0])
        assertEquals(UiMessage("Hello from agent!", isUser = false), messages[1])
        assertFalse(store.state.isLoading)

        store.dispose()
    }

    @Test
    fun `SendMessage adds error message on agent failure`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val agent = FakeAgent(response = Either.Left(AgentError.NetworkError("Timeout")))
        val store = ChatStoreFactory(DefaultStoreFactory(), agent, sessionManager).create()

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
    fun `SendMessage auto-titles session on first message`() = runTest {
        val sessionManager = InMemorySessionManager()
        val sessionId = sessionManager.createSession()
        val agent = FakeAgent(response = Either.Right("response"))
        val store = ChatStoreFactory(DefaultStoreFactory(), agent, sessionManager).create()

        store.accept(ChatStore.Intent.LoadSession(sessionId))
        advanceUntilIdle()

        store.accept(ChatStore.Intent.SendMessage("Hello world, this is a long message for auto-title testing purposes"))
        advanceUntilIdle()

        val title = sessionManager.getSession(sessionId)?.title ?: ""
        assertEquals("Hello world, this is a long message for auto-titl", title) // 50 chars

        store.dispose()
    }
}

class FakeAgent(
    private val response: Either<AgentError, String> = Either.Right(""),
) : Agent {
    override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, String> = response
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :compose-ui:test`

Expected: Compilation error — `ChatStoreFactory` constructor mismatch, `ChatStore.Intent.LoadSession` doesn't exist.

- [ ] **Step 3: Update `ChatStore.kt`**

Replace full content of `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt`:

```kotlin
package com.ai.challenge.ui.chat.store

import com.ai.challenge.session.SessionId
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
    )
}
```

- [ ] **Step 4: Update `ChatStoreFactory.kt`**

Replace full content of `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt`:

```kotlin
package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.agent.Agent
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.SessionId
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class ChatStoreFactory(
    private val storeFactory: StoreFactory,
    private val agent: Agent,
    private val sessionManager: AgentSessionManager,
) {
    fun create(): ChatStore =
        object : ChatStore,
            Store<ChatStore.Intent, ChatStore.State, Nothing> by storeFactory.create(
                name = "ChatStore",
                initialState = ChatStore.State(),
                executorFactory = { ExecutorImpl(agent, sessionManager) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class SessionLoaded(val sessionId: SessionId, val messages: List<UiMessage>) : Msg
        data class UserMessage(val text: String) : Msg
        data class AgentResponse(val text: String) : Msg
        data class Error(val text: String) : Msg
        data object Loading : Msg
        data object LoadingComplete : Msg
    }

    private class ExecutorImpl(
        private val agent: Agent,
        private val sessionManager: AgentSessionManager,
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
                        UiMessage(text = turn.userMessage, isUser = true),
                        UiMessage(text = turn.agentResponse, isUser = false),
                    )
                }
                dispatch(Msg.SessionLoaded(sessionId, messages))
            }
        }

        private fun handleSendMessage(text: String) {
            val sessionId = state().sessionId ?: return
            dispatch(Msg.UserMessage(text))
            dispatch(Msg.Loading)

            scope.launch {
                when (val result = agent.send(sessionId, text)) {
                    is Either.Right -> dispatch(Msg.AgentResponse(result.value))
                    is Either.Left -> dispatch(Msg.Error(result.value.message))
                }
                dispatch(Msg.LoadingComplete)

                // Auto-title on first message
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
                )
                is Msg.UserMessage -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = true),
                )
                is Msg.AgentResponse -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false),
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

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :compose-ui:test`

Expected: All 5 tests PASS

- [ ] **Step 6: Commit**

```bash
git add compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ compose-ui/src/test/
git commit -m "feat: update ChatStore with session loading and auto-title"
```

---

### Task 7: SessionListStore

**Files:**
- Create: `compose-ui/src/main/kotlin/com/ai/challenge/ui/sessionlist/store/SessionListStore.kt`
- Create: `compose-ui/src/main/kotlin/com/ai/challenge/ui/sessionlist/store/SessionListStoreFactory.kt`
- Create: `compose-ui/src/test/kotlin/com/ai/challenge/ui/sessionlist/store/SessionListStoreTest.kt`

- [ ] **Step 1: Write the tests first**

Create `compose-ui/src/test/kotlin/com/ai/challenge/ui/sessionlist/store/SessionListStoreTest.kt`:

```kotlin
package com.ai.challenge.ui.sessionlist.store

import com.ai.challenge.session.InMemorySessionManager
import com.ai.challenge.session.Turn
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
        val sessionManager = InMemorySessionManager()
        val store = SessionListStoreFactory(DefaultStoreFactory(), sessionManager).create()

        assertTrue(store.state.sessions.isEmpty())
        assertNull(store.state.activeSessionId)

        store.dispose()
    }

    @Test
    fun `LoadSessions populates session list`() = runTest {
        val sessionManager = InMemorySessionManager()
        val id1 = sessionManager.createSession(title = "Chat 1")
        val id2 = sessionManager.createSession(title = "Chat 2")

        val store = SessionListStoreFactory(DefaultStoreFactory(), sessionManager).create()
        store.accept(SessionListStore.Intent.LoadSessions)
        advanceUntilIdle()

        assertEquals(2, store.state.sessions.size)

        store.dispose()
    }

    @Test
    fun `CreateSession creates new session and makes it active`() = runTest {
        val sessionManager = InMemorySessionManager()
        val store = SessionListStoreFactory(DefaultStoreFactory(), sessionManager).create()

        store.accept(SessionListStore.Intent.CreateSession)
        advanceUntilIdle()

        assertEquals(1, store.state.sessions.size)
        assertNotNull(store.state.activeSessionId)
        assertEquals(store.state.sessions[0].id, store.state.activeSessionId)

        store.dispose()
    }

    @Test
    fun `DeleteSession removes session from list`() = runTest {
        val sessionManager = InMemorySessionManager()
        val id = sessionManager.createSession(title = "To delete")

        val store = SessionListStoreFactory(DefaultStoreFactory(), sessionManager).create()
        store.accept(SessionListStore.Intent.LoadSessions)
        advanceUntilIdle()

        store.accept(SessionListStore.Intent.DeleteSession(id))
        advanceUntilIdle()

        assertTrue(store.state.sessions.isEmpty())
        assertNull(sessionManager.getSession(id))

        store.dispose()
    }

    @Test
    fun `DeleteSession switches active to first remaining if active was deleted`() = runTest {
        val sessionManager = InMemorySessionManager()
        val id1 = sessionManager.createSession(title = "First")
        val id2 = sessionManager.createSession(title = "Second")

        val store = SessionListStoreFactory(DefaultStoreFactory(), sessionManager).create()
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
        val sessionManager = InMemorySessionManager()
        val id = sessionManager.createSession(title = "Test")

        val store = SessionListStoreFactory(DefaultStoreFactory(), sessionManager).create()
        store.accept(SessionListStore.Intent.LoadSessions)
        advanceUntilIdle()

        store.accept(SessionListStore.Intent.SelectSession(id))
        advanceUntilIdle()

        assertEquals(id, store.state.activeSessionId)

        store.dispose()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :compose-ui:test --tests "com.ai.challenge.ui.sessionlist.store.SessionListStoreTest"`

Expected: Compilation error — `SessionListStore` and `SessionListStoreFactory` don't exist.

- [ ] **Step 3: Create `SessionListStore.kt`**

Create `compose-ui/src/main/kotlin/com/ai/challenge/ui/sessionlist/store/SessionListStore.kt`:

```kotlin
package com.ai.challenge.ui.sessionlist.store

import com.ai.challenge.session.SessionId
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

- [ ] **Step 4: Create `SessionListStoreFactory.kt`**

Create `compose-ui/src/main/kotlin/com/ai/challenge/ui/sessionlist/store/SessionListStoreFactory.kt`:

```kotlin
package com.ai.challenge.ui.sessionlist.store

import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.SessionId
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class SessionListStoreFactory(
    private val storeFactory: StoreFactory,
    private val sessionManager: AgentSessionManager,
) {
    fun create(): SessionListStore =
        object : SessionListStore,
            Store<SessionListStore.Intent, SessionListStore.State, Nothing> by storeFactory.create(
                name = "SessionListStore",
                initialState = SessionListStore.State(),
                executorFactory = { ExecutorImpl(sessionManager) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class SessionsLoaded(val sessions: List<SessionListStore.SessionItem>, val activeSessionId: SessionId?) : Msg
        data class SessionCreated(val item: SessionListStore.SessionItem) : Msg
        data class SessionDeleted(val id: SessionId, val newActiveId: SessionId?) : Msg
        data class SessionSelected(val id: SessionId) : Msg
    }

    private class ExecutorImpl(
        private val sessionManager: AgentSessionManager,
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
                val sessions = sessionManager.listSessions().map { session ->
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
                val id = sessionManager.createSession()
                val session = sessionManager.getSession(id)!!
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
                sessionManager.deleteSession(id)
                val remaining = sessionManager.listSessions()
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

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :compose-ui:test`

Expected: All tests PASS (ChatStoreTest + SessionListStoreTest)

- [ ] **Step 6: Commit**

```bash
git add compose-ui/src/main/kotlin/com/ai/challenge/ui/sessionlist/ compose-ui/src/test/kotlin/com/ai/challenge/ui/sessionlist/
git commit -m "feat: add SessionListStore for managing multiple chat sessions"
```

---

### Task 8: Update ChatComponent, RootComponent, and DI

**Files:**
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatComponent.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootComponent.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/di/AppModule.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/main.kt`

- [ ] **Step 1: Update `ChatComponent.kt`**

Replace full content of `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatComponent.kt`:

```kotlin
package com.ai.challenge.ui.chat

import com.ai.challenge.agent.Agent
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.SessionId
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
    sessionId: SessionId,
) : ComponentContext by componentContext {

    private val store = instanceKeeper.getStore {
        ChatStoreFactory(storeFactory, agent, sessionManager).create()
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

- [ ] **Step 2: Update `RootComponent.kt`**

Replace full content of `compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootComponent.kt`:

```kotlin
package com.ai.challenge.ui.root

import com.ai.challenge.agent.Agent
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.SessionId
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
import kotlinx.serialization.Serializable

class RootComponent(
    componentContext: ComponentContext,
    private val storeFactory: StoreFactory,
    private val agent: Agent,
    private val sessionManager: AgentSessionManager,
) : ComponentContext by componentContext {

    private val sessionListStore = instanceKeeper.getStore {
        SessionListStoreFactory(storeFactory, sessionManager).create()
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
        // Load sessions on startup; if empty, create first session
        val sessions = sessionManager.listSessions()
        if (sessions.isEmpty()) {
            val id = sessionManager.createSession()
            sessionListStore.accept(SessionListStore.Intent.LoadSessions)
            selectSession(id)
        } else {
            sessionListStore.accept(SessionListStore.Intent.LoadSessions)
            selectSession(sessions.first().id)
        }
    }

    fun selectSession(sessionId: SessionId) {
        sessionListStore.accept(SessionListStore.Intent.SelectSession(sessionId))
        navigation.replaceCurrent(Config.Chat(sessionId = sessionId.value))
    }

    fun createNewSession() {
        val id = sessionManager.createSession()
        sessionListStore.accept(SessionListStore.Intent.LoadSessions)
        selectSession(id)
    }

    fun deleteSession(sessionId: SessionId) {
        sessionManager.deleteSession(sessionId)
        sessionListStore.accept(SessionListStore.Intent.LoadSessions)

        val remaining = sessionManager.listSessions()
        if (remaining.isEmpty()) {
            createNewSession()
        } else {
            val currentActive = sessionListStore.stateFlow.value.activeSessionId
            if (currentActive == sessionId) {
                selectSession(remaining.first().id)
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
                    sessionManager = sessionManager,
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

- [ ] **Step 3: Update `AppModule.kt`**

Replace full content of `compose-ui/src/main/kotlin/com/ai/challenge/ui/di/AppModule.kt`:

```kotlin
package com.ai.challenge.ui.di

import com.ai.challenge.agent.Agent
import com.ai.challenge.agent.OpenRouterAgent
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.ExposedSessionManager
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
    single<Agent> { OpenRouterAgent(get(), model = "google/gemini-2.0-flash-001", get()) }
}
```

- [ ] **Step 4: Update `main.kt`**

Replace full content of `compose-ui/src/main/kotlin/com/ai/challenge/ui/main.kt`:

```kotlin
package com.ai.challenge.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ai.challenge.agent.Agent
import com.ai.challenge.session.AgentSessionManager
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
    val rootComponentContext = DefaultComponentContext(lifecycle = lifecycle)

    val root = RootComponent(
        componentContext = rootComponentContext,
        storeFactory = DefaultStoreFactory(),
        agent = koin.get<Agent>(),
        sessionManager = koin.get<AgentSessionManager>(),
    )

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

- [ ] **Step 5: Verify compilation**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :compose-ui:compileKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Run all tests**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew test`

Expected: All tests PASS across all modules

- [ ] **Step 7: Commit**

```bash
git add compose-ui/src/main/kotlin/
git commit -m "feat: wire session management into DI, ChatComponent, and RootComponent"
```

---

### Task 9: Drawer UI

**Files:**
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootContent.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt`

- [ ] **Step 1: Update `RootContent.kt` with drawer**

Replace full content of `compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootContent.kt`:

```kotlin
package com.ai.challenge.ui.root

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.challenge.ui.chat.ChatContent
import com.ai.challenge.ui.sessionlist.store.SessionListStore
import com.arkivanov.decompose.extensions.compose.stack.Children
import kotlinx.coroutines.launch

@Composable
fun RootContent(component: RootComponent) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sessionListState by component.sessionListState.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                state = sessionListState,
                onNewChat = {
                    component.createNewSession()
                    scope.launch { drawerState.close() }
                },
                onSelectSession = { sessionId ->
                    component.selectSession(sessionId)
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = { sessionId ->
                    component.deleteSession(sessionId)
                },
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with hamburger
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(Icons.Default.Menu, contentDescription = "Open sessions")
                }
                Text(
                    text = "AI Chat",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            HorizontalDivider()

            Children(stack = component.childStack) { child ->
                when (val instance = child.instance) {
                    is RootComponent.Child.Chat -> ChatContent(instance.component)
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    state: SessionListStore.State,
    onNewChat: () -> Unit,
    onSelectSession: (com.ai.challenge.session.SessionId) -> Unit,
    onDeleteSession: (com.ai.challenge.session.SessionId) -> Unit,
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Sessions",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onNewChat) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("New chat", modifier = Modifier.padding(start = 8.dp))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.sessions, key = { it.id.value }) { session ->
                    SessionRow(
                        session = session,
                        isActive = session.id == state.activeSessionId,
                        onSelect = { onSelectSession(session.id) },
                        onDelete = { onDeleteSession(session.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionListStore.SessionItem,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val backgroundColor = if (isActive) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = session.title.ifEmpty { "New chat" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete session",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 2: Remove Scaffold from `ChatContent.kt` (top bar now in RootContent)**

In `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt`, replace the `Scaffold` wrapper with a plain `Column`. Replace the full content:

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

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages) { message ->
                MessageBubble(message)
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
    }
}

@Composable
private fun MessageBubble(message: UiMessage) {
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
        Text(
            text = message.text,
            modifier = Modifier
                .widthIn(max = 600.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .padding(12.dp),
            color = textColor,
        )
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :compose-ui:compileKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run all tests**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew test`

Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add compose-ui/src/main/kotlin/
git commit -m "feat: add drawer UI for session management"
```

---

### Task 10: Final integration verification

**Files:** None (manual testing)

- [ ] **Step 1: Run full build**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew build`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all tests**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew test`

Expected: All tests PASS across session-storage, ai-agent, compose-ui

- [ ] **Step 3: Verify DB file location**

After running the app once, check:

Run: `ls -la ~/.ai-challenge/sessions.db`

Expected: File exists

- [ ] **Step 4: Commit any remaining changes**

If there are any tweaks needed from integration testing:

```bash
git add -A
git commit -m "fix: integration adjustments for session memory"
```
