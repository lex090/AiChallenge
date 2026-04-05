# Context Compression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add context compression to the AI agent so that older conversation history is replaced with LLM-generated summaries, reducing token usage while preserving conversation quality.

**Architecture:** ContextManager is a pure transformation — takes full turn history and returns prepared messages (summary + recent turns). CompressionStrategy decides when to compress, ContextCompressor generates summaries via LLM, SummaryRepository caches them in DB. AiAgent gets one new dependency and ~3 lines changed in `send()`.

**Tech Stack:** Kotlin 2.3.20, Exposed (SQLite), Ktor (MockEngine for tests), Arrow Either, kotlin.time.Instant

---

### Task 1: Core Models — MessageRole, ContextMessage, CompressedContext

**Files:**
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/MessageRole.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/ContextMessage.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/CompressedContext.kt`

- [ ] **Step 1: Create MessageRole enum**

Create file `modules/core/src/main/kotlin/com/ai/challenge/core/MessageRole.kt`:

```kotlin
package com.ai.challenge.core

enum class MessageRole {
    System,
    User,
    Assistant,
}
```

- [ ] **Step 2: Create ContextMessage data class**

Create file `modules/core/src/main/kotlin/com/ai/challenge/core/ContextMessage.kt`:

```kotlin
package com.ai.challenge.core

data class ContextMessage(
    val role: MessageRole,
    val content: String,
)
```

- [ ] **Step 3: Create CompressedContext data class**

Create file `modules/core/src/main/kotlin/com/ai/challenge/core/CompressedContext.kt`:

```kotlin
package com.ai.challenge.core

data class CompressedContext(
    val messages: List<ContextMessage>,
    val compressed: Boolean,
    val originalTurnCount: Int,
    val retainedTurnCount: Int,
    val summaryCount: Int,
)
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :modules:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/MessageRole.kt \
       modules/core/src/main/kotlin/com/ai/challenge/core/ContextMessage.kt \
       modules/core/src/main/kotlin/com/ai/challenge/core/CompressedContext.kt
git commit -m "feat(core): add MessageRole, ContextMessage, CompressedContext models"
```

---

### Task 2: Core Interfaces — CompressionStrategy, ContextCompressor, ContextManager

**Files:**
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/CompressionStrategy.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/ContextCompressor.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/ContextManager.kt`

- [ ] **Step 1: Create CompressionStrategy interface**

Create file `modules/core/src/main/kotlin/com/ai/challenge/core/CompressionStrategy.kt`:

```kotlin
package com.ai.challenge.core

interface CompressionStrategy {
    fun shouldCompress(history: List<Turn>): Boolean
    fun partitionPoint(history: List<Turn>): Int
}
```

- [ ] **Step 2: Create ContextCompressor interface**

Create file `modules/core/src/main/kotlin/com/ai/challenge/core/ContextCompressor.kt`:

```kotlin
package com.ai.challenge.core

interface ContextCompressor {
    suspend fun compress(turns: List<Turn>): String
}
```

- [ ] **Step 3: Create ContextManager interface**

Create file `modules/core/src/main/kotlin/com/ai/challenge/core/ContextManager.kt`:

```kotlin
package com.ai.challenge.core

interface ContextManager {
    suspend fun prepareContext(
        sessionId: SessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext
}
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :modules:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/CompressionStrategy.kt \
       modules/core/src/main/kotlin/com/ai/challenge/core/ContextCompressor.kt \
       modules/core/src/main/kotlin/com/ai/challenge/core/ContextManager.kt
git commit -m "feat(core): add CompressionStrategy, ContextCompressor, ContextManager interfaces"
```

---

### Task 3: Core Models — Summary, SummaryId, SummaryRepository

**Files:**
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/SummaryId.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/Summary.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/SummaryRepository.kt`

- [ ] **Step 1: Create SummaryId value class**

Create file `modules/core/src/main/kotlin/com/ai/challenge/core/SummaryId.kt`:

```kotlin
package com.ai.challenge.core

import java.util.UUID

@JvmInline
value class SummaryId(val value: String) {
    companion object {
        fun generate(): SummaryId = SummaryId(UUID.randomUUID().toString())
    }
}
```

- [ ] **Step 2: Create Summary data class**

Create file `modules/core/src/main/kotlin/com/ai/challenge/core/Summary.kt`:

```kotlin
package com.ai.challenge.core

import kotlin.time.Clock
import kotlin.time.Instant

data class Summary(
    val id: SummaryId = SummaryId.generate(),
    val text: String,
    val fromTurnIndex: Int,
    val toTurnIndex: Int,
    val createdAt: Instant = Clock.System.now(),
)
```

- [ ] **Step 3: Create SummaryRepository interface**

Create file `modules/core/src/main/kotlin/com/ai/challenge/core/SummaryRepository.kt`:

```kotlin
package com.ai.challenge.core

interface SummaryRepository {
    suspend fun save(sessionId: SessionId, summary: Summary)
    suspend fun getBySession(sessionId: SessionId): List<Summary>
}
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :modules:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/SummaryId.kt \
       modules/core/src/main/kotlin/com/ai/challenge/core/Summary.kt \
       modules/core/src/main/kotlin/com/ai/challenge/core/SummaryRepository.kt
git commit -m "feat(core): add Summary, SummaryId, SummaryRepository for context compression"
```

---

### Task 4: TurnCountStrategy — Tests and Implementation

**Files:**
- Create: `modules/domain/context-manager/build.gradle.kts`
- Create: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/TurnCountStrategy.kt`
- Create: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/TurnCountStrategyTest.kt`
- Modify: `settings.gradle.kts` — add module include

- [ ] **Step 1: Register module in settings.gradle.kts**

Add to `settings.gradle.kts` under `// Layer 2: Domain`:

```kotlin
include(":modules:domain:context-manager")
```

- [ ] **Step 2: Create build.gradle.kts for context-manager module**

Create file `modules/domain/context-manager/build.gradle.kts`:

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
    implementation(project(":modules:core"))

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

- [ ] **Step 3: Write the failing tests**

Create file `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/TurnCountStrategyTest.kt`:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.Turn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TurnCountStrategyTest {

    private fun turns(count: Int): List<Turn> =
        (1..count).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") }

    @Test
    fun `shouldCompress returns false when history size equals maxTurns`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2)
        assertFalse(strategy.shouldCompress(turns(5)))
    }

    @Test
    fun `shouldCompress returns false when history is smaller than maxTurns`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2)
        assertFalse(strategy.shouldCompress(turns(3)))
    }

    @Test
    fun `shouldCompress returns true when history exceeds maxTurns`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2)
        assertTrue(strategy.shouldCompress(turns(6)))
    }

    @Test
    fun `shouldCompress returns false for empty history`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 2)
        assertFalse(strategy.shouldCompress(emptyList()))
    }

    @Test
    fun `partitionPoint returns correct split index`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 3)
        assertEquals(7, strategy.partitionPoint(turns(10)))
    }

    @Test
    fun `partitionPoint returns 0 when retainLast exceeds history size`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 20)
        assertEquals(0, strategy.partitionPoint(turns(10)))
    }

    @Test
    fun `partitionPoint returns 0 for empty history`() {
        val strategy = TurnCountStrategy(maxTurns = 5, retainLast = 3)
        assertEquals(0, strategy.partitionPoint(emptyList()))
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew :modules:domain:context-manager:test`
Expected: FAIL — `TurnCountStrategy` class not found

- [ ] **Step 5: Implement TurnCountStrategy**

Create file `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/TurnCountStrategy.kt`:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.CompressionStrategy
import com.ai.challenge.core.Turn

class TurnCountStrategy(
    private val maxTurns: Int,
    private val retainLast: Int,
) : CompressionStrategy {

    override fun shouldCompress(history: List<Turn>): Boolean =
        history.size > maxTurns

    override fun partitionPoint(history: List<Turn>): Int =
        (history.size - retainLast).coerceAtLeast(0)
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :modules:domain:context-manager:test`
Expected: All 7 tests PASS

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts \
       modules/domain/context-manager/build.gradle.kts \
       modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/TurnCountStrategy.kt \
       modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/TurnCountStrategyTest.kt
git commit -m "feat(context-manager): add TurnCountStrategy with tests"
```

---

### Task 5: DefaultContextManager — Tests and Implementation

**Files:**
- Create: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt`
- Create: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt`

- [ ] **Step 1: Write the failing tests**

Create file `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt`:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.ContextCompressor
import com.ai.challenge.core.ContextMessage
import com.ai.challenge.core.MessageRole
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Summary
import com.ai.challenge.core.SummaryRepository
import com.ai.challenge.core.Turn
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultContextManagerTest {

    private fun turns(count: Int): List<Turn> =
        (1..count).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") }

    private val fakeCompressor = FakeContextCompressor()
    private val fakeSummaryRepo = InMemorySummaryRepository()

    private fun createManager(maxTurns: Int = 3, retainLast: Int = 1): DefaultContextManager =
        DefaultContextManager(
            strategy = TurnCountStrategy(maxTurns = maxTurns, retainLast = retainLast),
            compressor = fakeCompressor,
            summaryRepository = fakeSummaryRepo,
        )

    @Test
    fun `returns all turns when history is below threshold`() = runTest {
        val manager = createManager(maxTurns = 5, retainLast = 2)
        val history = turns(3)

        val result = manager.prepareContext(SessionId("s1"), history, "new msg")

        assertFalse(result.compressed)
        assertEquals(3, result.originalTurnCount)
        assertEquals(3, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(7, result.messages.size) // 3 turns * 2 messages + 1 new message
        assertEquals(ContextMessage(MessageRole.User, "new msg"), result.messages.last())
    }

    @Test
    fun `compresses old turns and retains recent ones`() = runTest {
        val manager = createManager(maxTurns = 3, retainLast = 2)
        val history = turns(5)

        val result = manager.prepareContext(SessionId("s1"), history, "new msg")

        assertTrue(result.compressed)
        assertEquals(5, result.originalTurnCount)
        assertEquals(2, result.retainedTurnCount)
        assertEquals(1, result.summaryCount)
        // 1 system (summary) + 2 retained turns * 2 + 1 new message = 6
        assertEquals(6, result.messages.size)
        assertEquals(MessageRole.System, result.messages.first().role)
        assertTrue(result.messages.first().content.contains("Summary of 3 turns"))
        assertEquals(ContextMessage(MessageRole.User, "msg4"), result.messages[1])
        assertEquals(ContextMessage(MessageRole.Assistant, "resp4"), result.messages[2])
        assertEquals(ContextMessage(MessageRole.User, "msg5"), result.messages[3])
        assertEquals(ContextMessage(MessageRole.Assistant, "resp5"), result.messages[4])
        assertEquals(ContextMessage(MessageRole.User, "new msg"), result.messages[5])
    }

    @Test
    fun `uses cached summary when range matches`() = runTest {
        val manager = createManager(maxTurns = 3, retainLast = 2)
        val history = turns(5)
        val sessionId = SessionId("s1")

        manager.prepareContext(sessionId, history, "first call")
        assertEquals(1, fakeCompressor.callCount)

        manager.prepareContext(sessionId, history, "second call")
        assertEquals(1, fakeCompressor.callCount) // not called again
    }

    @Test
    fun `creates new summary when range shifts`() = runTest {
        val manager = createManager(maxTurns = 3, retainLast = 2)
        val sessionId = SessionId("s1")

        manager.prepareContext(sessionId, turns(5), "call1")
        assertEquals(1, fakeCompressor.callCount)

        // History grew — splitAt shifts from 3 to 5
        manager.prepareContext(sessionId, turns(7), "call2")
        assertEquals(2, fakeCompressor.callCount)
    }

    @Test
    fun `handles empty history`() = runTest {
        val manager = createManager(maxTurns = 3, retainLast = 2)

        val result = manager.prepareContext(SessionId("s1"), emptyList(), "hello")

        assertFalse(result.compressed)
        assertEquals(0, result.originalTurnCount)
        assertEquals(1, result.messages.size)
        assertEquals(ContextMessage(MessageRole.User, "hello"), result.messages[0])
    }
}

private class FakeContextCompressor : ContextCompressor {
    var callCount = 0
        private set

    override suspend fun compress(turns: List<Turn>): String {
        callCount++
        return "Summary of ${turns.size} turns"
    }
}

private class InMemorySummaryRepository : SummaryRepository {
    private val store = mutableListOf<Pair<SessionId, Summary>>()

    override suspend fun save(sessionId: SessionId, summary: Summary) {
        store.add(sessionId to summary)
    }

    override suspend fun getBySession(sessionId: SessionId): List<Summary> =
        store.filter { it.first == sessionId }.map { it.second }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :modules:domain:context-manager:test`
Expected: FAIL — `DefaultContextManager` class not found

- [ ] **Step 3: Implement DefaultContextManager**

Create file `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt`:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.CompressedContext
import com.ai.challenge.core.ContextCompressor
import com.ai.challenge.core.ContextManager
import com.ai.challenge.core.ContextMessage
import com.ai.challenge.core.CompressionStrategy
import com.ai.challenge.core.MessageRole
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Summary
import com.ai.challenge.core.SummaryRepository
import com.ai.challenge.core.Turn

class DefaultContextManager(
    private val strategy: CompressionStrategy,
    private val compressor: ContextCompressor,
    private val summaryRepository: SummaryRepository,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: SessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext {

        if (!strategy.shouldCompress(history)) {
            val messages = buildList {
                for (turn in history) {
                    add(ContextMessage(MessageRole.User, turn.userMessage))
                    add(ContextMessage(MessageRole.Assistant, turn.agentResponse))
                }
                add(ContextMessage(MessageRole.User, newMessage))
            }
            return CompressedContext(
                messages = messages,
                compressed = false,
                originalTurnCount = history.size,
                retainedTurnCount = history.size,
                summaryCount = 0,
            )
        }

        val splitAt = strategy.partitionPoint(history)
        val toCompress = history.subList(0, splitAt)
        val toRetain = history.subList(splitAt, history.size)

        val existingSummaries = summaryRepository.getBySession(sessionId)
        val cached = existingSummaries.find {
            it.fromTurnIndex == 0 && it.toTurnIndex == splitAt
        }

        val summaryText = if (cached != null) {
            cached.text
        } else {
            val text = compressor.compress(toCompress)
            summaryRepository.save(
                sessionId,
                Summary(
                    text = text,
                    fromTurnIndex = 0,
                    toTurnIndex = splitAt,
                ),
            )
            text
        }

        val messages = buildList {
            add(ContextMessage(MessageRole.System, "Previous conversation summary:\n$summaryText"))
            for (turn in toRetain) {
                add(ContextMessage(MessageRole.User, turn.userMessage))
                add(ContextMessage(MessageRole.Assistant, turn.agentResponse))
            }
            add(ContextMessage(MessageRole.User, newMessage))
        }

        return CompressedContext(
            messages = messages,
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = toRetain.size,
            summaryCount = 1,
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :modules:domain:context-manager:test`
Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt \
       modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt
git commit -m "feat(context-manager): add DefaultContextManager with tests"
```

---

### Task 6: ExposedSummaryRepository — Module Setup, Tests, and Implementation

**Files:**
- Create: `modules/data/summary-repository-exposed/build.gradle.kts`
- Create: `modules/data/summary-repository-exposed/src/main/kotlin/com/ai/challenge/summary/repository/SummariesTable.kt`
- Create: `modules/data/summary-repository-exposed/src/main/kotlin/com/ai/challenge/summary/repository/ExposedSummaryRepository.kt`
- Create: `modules/data/summary-repository-exposed/src/main/kotlin/com/ai/challenge/summary/repository/DatabaseFactory.kt`
- Create: `modules/data/summary-repository-exposed/src/test/kotlin/com/ai/challenge/summary/repository/ExposedSummaryRepositoryTest.kt`
- Modify: `settings.gradle.kts` — add module include

- [ ] **Step 1: Register module in settings.gradle.kts**

Add to `settings.gradle.kts` under `// Layer 1: Data`:

```kotlin
include(":modules:data:summary-repository-exposed")
```

- [ ] **Step 2: Create build.gradle.kts**

Create file `modules/data/summary-repository-exposed/build.gradle.kts`:

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
    implementation(project(":modules:core"))
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

- [ ] **Step 3: Write the failing test**

Create file `modules/data/summary-repository-exposed/src/test/kotlin/com/ai/challenge/summary/repository/ExposedSummaryRepositoryTest.kt`:

```kotlin
package com.ai.challenge.summary.repository

import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Summary
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExposedSummaryRepositoryTest {

    private lateinit var repo: ExposedSummaryRepository

    @BeforeTest
    fun setup() {
        val db = Database.connect("jdbc:sqlite::memory:", driver = "org.sqlite.JDBC")
        repo = ExposedSummaryRepository(db)
    }

    @Test
    fun `save and retrieve summary by session`() = runTest {
        val sessionId = SessionId("s1")
        val summary = Summary(text = "test summary", fromTurnIndex = 0, toTurnIndex = 5)

        repo.save(sessionId, summary)
        val result = repo.getBySession(sessionId)

        assertEquals(1, result.size)
        assertEquals("test summary", result[0].text)
        assertEquals(0, result[0].fromTurnIndex)
        assertEquals(5, result[0].toTurnIndex)
    }

    @Test
    fun `getBySession returns empty list for unknown session`() = runTest {
        val result = repo.getBySession(SessionId("unknown"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `multiple summaries for same session`() = runTest {
        val sessionId = SessionId("s1")

        repo.save(sessionId, Summary(text = "summary1", fromTurnIndex = 0, toTurnIndex = 3))
        repo.save(sessionId, Summary(text = "summary2", fromTurnIndex = 0, toTurnIndex = 5))

        val result = repo.getBySession(sessionId)
        assertEquals(2, result.size)
    }

    @Test
    fun `summaries from different sessions are isolated`() = runTest {
        repo.save(SessionId("s1"), Summary(text = "s1 summary", fromTurnIndex = 0, toTurnIndex = 3))
        repo.save(SessionId("s2"), Summary(text = "s2 summary", fromTurnIndex = 0, toTurnIndex = 3))

        val s1Result = repo.getBySession(SessionId("s1"))
        assertEquals(1, s1Result.size)
        assertEquals("s1 summary", s1Result[0].text)

        val s2Result = repo.getBySession(SessionId("s2"))
        assertEquals(1, s2Result.size)
        assertEquals("s2 summary", s2Result[0].text)
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :modules:data:summary-repository-exposed:test`
Expected: FAIL — classes not found

- [ ] **Step 5: Create SummariesTable**

Create file `modules/data/summary-repository-exposed/src/main/kotlin/com/ai/challenge/summary/repository/SummariesTable.kt`:

```kotlin
package com.ai.challenge.summary.repository

import org.jetbrains.exposed.sql.Table

object SummariesTable : Table("summaries") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val text = text("text")
    val fromTurnIndex = integer("from_turn_index")
    val toTurnIndex = integer("to_turn_index")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
```

- [ ] **Step 6: Implement ExposedSummaryRepository**

Create file `modules/data/summary-repository-exposed/src/main/kotlin/com/ai/challenge/summary/repository/ExposedSummaryRepository.kt`:

```kotlin
package com.ai.challenge.summary.repository

import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Summary
import com.ai.challenge.core.SummaryId
import com.ai.challenge.core.SummaryRepository
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Instant

class ExposedSummaryRepository(private val database: Database) : SummaryRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(SummariesTable)
        }
    }

    override suspend fun save(sessionId: SessionId, summary: Summary) {
        transaction(database) {
            SummariesTable.insert {
                it[id] = summary.id.value
                it[SummariesTable.sessionId] = sessionId.value
                it[text] = summary.text
                it[fromTurnIndex] = summary.fromTurnIndex
                it[toTurnIndex] = summary.toTurnIndex
                it[createdAt] = summary.createdAt.toEpochMilliseconds()
            }
        }
    }

    override suspend fun getBySession(sessionId: SessionId): List<Summary> = transaction(database) {
        SummariesTable.selectAll()
            .where { SummariesTable.sessionId eq sessionId.value }
            .map { it.toSummary() }
    }

    private fun ResultRow.toSummary() = Summary(
        id = SummaryId(this[SummariesTable.id]),
        text = this[SummariesTable.text],
        fromTurnIndex = this[SummariesTable.fromTurnIndex],
        toTurnIndex = this[SummariesTable.toTurnIndex],
        createdAt = Instant.fromEpochMilliseconds(this[SummariesTable.createdAt]),
    )
}
```

- [ ] **Step 7: Create DatabaseFactory**

Create file `modules/data/summary-repository-exposed/src/main/kotlin/com/ai/challenge/summary/repository/DatabaseFactory.kt`:

```kotlin
package com.ai.challenge.summary.repository

import org.jetbrains.exposed.sql.Database
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

fun createSummaryDatabase(): Database {
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

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew :modules:data:summary-repository-exposed:test`
Expected: All 4 tests PASS

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts \
       modules/data/summary-repository-exposed/build.gradle.kts \
       modules/data/summary-repository-exposed/src/main/kotlin/com/ai/challenge/summary/repository/SummariesTable.kt \
       modules/data/summary-repository-exposed/src/main/kotlin/com/ai/challenge/summary/repository/ExposedSummaryRepository.kt \
       modules/data/summary-repository-exposed/src/main/kotlin/com/ai/challenge/summary/repository/DatabaseFactory.kt \
       modules/data/summary-repository-exposed/src/test/kotlin/com/ai/challenge/summary/repository/ExposedSummaryRepositoryTest.kt
git commit -m "feat(summary-repository): add ExposedSummaryRepository with tests"
```

---

### Task 7: LlmContextCompressor — Module Setup, Tests, and Implementation

**Files:**
- Create: `modules/data/context-compressor-llm/build.gradle.kts`
- Create: `modules/data/context-compressor-llm/src/main/kotlin/com/ai/challenge/compressor/LlmContextCompressor.kt`
- Create: `modules/data/context-compressor-llm/src/test/kotlin/com/ai/challenge/compressor/LlmContextCompressorTest.kt`
- Modify: `settings.gradle.kts` — add module include

- [ ] **Step 1: Register module in settings.gradle.kts**

Add to `settings.gradle.kts` under `// Layer 1: Data`:

```kotlin
include(":modules:data:context-compressor-llm")
```

- [ ] **Step 2: Create build.gradle.kts**

Create file `modules/data/context-compressor-llm/build.gradle.kts`:

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
    implementation(project(":modules:core"))
    implementation(project(":modules:data:open-router-service"))

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 3: Write the failing test**

Create file `modules/data/context-compressor-llm/src/test/kotlin/com/ai/challenge/compressor/LlmContextCompressorTest.kt`:

```kotlin
package com.ai.challenge.compressor

import com.ai.challenge.core.Turn
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LlmContextCompressorTest {

    @Test
    fun `compress sends conversation to LLM and returns summary text`() = runTest {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"choices":[{"index":0,"message":{"role":"assistant","content":"This is a summary."}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = false }) }
        }
        val service = OpenRouterService(apiKey = "test-key", client = client)
        val compressor = LlmContextCompressor(service = service, model = "test-model")

        val turns = listOf(
            Turn(userMessage = "Hello", agentResponse = "Hi there!"),
            Turn(userMessage = "How are you?", agentResponse = "I'm fine!"),
        )

        val result = compressor.compress(turns)

        assertEquals("This is a summary.", result)

        val json = Json.parseToJsonElement(capturedBody!!).jsonObject
        val messages = json["messages"]!!.jsonArray

        // system + 2 user + 2 assistant + final user instruction = 6
        assertEquals(6, messages.size)
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Hello", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("assistant", messages[2].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Hi there!", messages[2].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[3].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("How are you?", messages[3].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("assistant", messages[4].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("I'm fine!", messages[4].jsonObject["content"]!!.jsonPrimitive.content)
        // Last message is the summarization instruction
        assertEquals("user", messages[5].jsonObject["role"]!!.jsonPrimitive.content)
        assertTrue(messages[5].jsonObject["content"]!!.jsonPrimitive.content.contains("summary"))
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :modules:data:context-compressor-llm:test`
Expected: FAIL — `LlmContextCompressor` class not found

- [ ] **Step 5: Implement LlmContextCompressor**

Create file `modules/data/context-compressor-llm/src/main/kotlin/com/ai/challenge/compressor/LlmContextCompressor.kt`:

```kotlin
package com.ai.challenge.compressor

import com.ai.challenge.core.ContextCompressor
import com.ai.challenge.core.Turn
import com.ai.challenge.llm.OpenRouterService

class LlmContextCompressor(
    private val service: OpenRouterService,
    private val model: String,
) : ContextCompressor {

    override suspend fun compress(turns: List<Turn>): String {
        return service.chatText(model) {
            system("Summarize the following conversation concisely, preserving key facts, decisions, and context needed for continuation.")
            for (turn in turns) {
                user(turn.userMessage)
                assistant(turn.agentResponse)
            }
            user("Provide a concise summary of the conversation above.")
        }
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :modules:data:context-compressor-llm:test`
Expected: 1 test PASS

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts \
       modules/data/context-compressor-llm/build.gradle.kts \
       modules/data/context-compressor-llm/src/main/kotlin/com/ai/challenge/compressor/LlmContextCompressor.kt \
       modules/data/context-compressor-llm/src/test/kotlin/com/ai/challenge/compressor/LlmContextCompressorTest.kt
git commit -m "feat(compressor): add LlmContextCompressor with MockEngine test"
```

---

### Task 8: Integrate ContextManager into AiAgent

**Files:**
- Modify: `modules/domain/ai-agent/build.gradle.kts` — add core dependency (already has it)
- Modify: `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiAgent.kt:22-49` — add ContextManager parameter and use it in send()
- Modify: `modules/domain/ai-agent/src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt` — update tests to provide ContextManager

- [ ] **Step 1: Update existing tests to pass a ContextManager**

The existing tests need a `ContextManager` that passes through all turns without compression (a no-op implementation). Modify `modules/domain/ai-agent/src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt`.

Add this fake class at the bottom of the file (alongside other fakes):

```kotlin
private class PassThroughContextManager : ContextManager {
    override suspend fun prepareContext(
        sessionId: SessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext {
        val messages = buildList {
            for (turn in history) {
                add(ContextMessage(MessageRole.User, turn.userMessage))
                add(ContextMessage(MessageRole.Assistant, turn.agentResponse))
            }
            add(ContextMessage(MessageRole.User, newMessage))
        }
        return CompressedContext(
            messages = messages,
            compressed = false,
            originalTurnCount = history.size,
            retainedTurnCount = history.size,
            summaryCount = 0,
        )
    }
}
```

Add the required imports at the top:

```kotlin
import com.ai.challenge.core.CompressedContext
import com.ai.challenge.core.ContextManager
import com.ai.challenge.core.ContextMessage
import com.ai.challenge.core.MessageRole
```

Add instance to test class:

```kotlin
private val contextManager = PassThroughContextManager()
```

Update `createAgent` method to pass `contextManager`:

```kotlin
private fun createAgent(responseJson: String): AiAgent =
    AiAgent(
        service = createService(responseJson),
        model = "test-model",
        sessionRepository = sessionRepo,
        turnRepository = turnRepo,
        tokenRepository = tokenRepo,
        costRepository = costRepo,
        contextManager = contextManager,
    )
```

Update the `send returns Left NetworkError when service throws` test — it also creates AiAgent directly:

```kotlin
val agent = AiAgent(service, "test-model", sessionRepo, turnRepo, tokenRepo, costRepo, contextManager)
```

Update the `send includes history in LLM request` test — same direct construction:

```kotlin
val agent = AiAgent(service, "test-model", sessionRepo, turnRepo, tokenRepo, costRepo, contextManager)
```

- [ ] **Step 2: Run existing tests to verify they fail**

Run: `./gradlew :modules:domain:ai-agent:test`
Expected: FAIL — AiAgent constructor doesn't accept contextManager yet

- [ ] **Step 3: Create MessageRole.toApiRole() extension**

Add to `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiAgent.kt` (at file bottom, after the class):

```kotlin
fun MessageRole.toApiRole(): String = when (this) {
    MessageRole.System -> "system"
    MessageRole.User -> "user"
    MessageRole.Assistant -> "assistant"
}
```

- [ ] **Step 4: Update AiAgent to accept and use ContextManager**

Modify `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiAgent.kt`.

Update the constructor to add `contextManager` parameter:

```kotlin
class AiAgent(
    private val service: OpenRouterService,
    private val model: String,
    private val sessionRepository: SessionRepository,
    private val turnRepository: TurnRepository,
    private val tokenRepository: TokenRepository,
    private val costRepository: CostRepository,
    private val contextManager: ContextManager,
) : Agent {
```

Add imports:

```kotlin
import com.ai.challenge.core.ContextManager
import com.ai.challenge.core.MessageRole
```

Replace the history iteration block in `send()`. Change from:

```kotlin
val chatResponse = catch({
    service.chat(model = model) {
        for (turn in history) {
            user(turn.userMessage)
            assistant(turn.agentResponse)
        }
        user(message)
    }
}) { e: Exception ->
```

To:

```kotlin
val context = contextManager.prepareContext(sessionId, history, message)

val chatResponse = catch({
    service.chat(model = model) {
        for (msg in context.messages) {
            message(msg.role.toApiRole(), msg.content)
        }
    }
}) { e: Exception ->
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :modules:domain:ai-agent:test`
Expected: All 9 existing tests PASS

- [ ] **Step 6: Commit**

```bash
git add modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiAgent.kt \
       modules/domain/ai-agent/src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt
git commit -m "feat(ai-agent): integrate ContextManager into AiAgent.send()"
```

---

### Task 9: Wire DI and Update App Module

**Files:**
- Modify: `modules/presentation/app/build.gradle.kts` — add new module dependencies
- Modify: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt` — add new bindings

- [ ] **Step 1: Add module dependencies to app build.gradle.kts**

Add these three lines to the `dependencies` block in `modules/presentation/app/build.gradle.kts`:

```kotlin
implementation(project(":modules:domain:context-manager"))
implementation(project(":modules:data:context-compressor-llm"))
implementation(project(":modules:data:summary-repository-exposed"))
```

- [ ] **Step 2: Update AppModule.kt with new DI bindings**

Modify `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt`.

Add imports:

```kotlin
import com.ai.challenge.compressor.LlmContextCompressor
import com.ai.challenge.context.DefaultContextManager
import com.ai.challenge.context.TurnCountStrategy
import com.ai.challenge.core.CompressionStrategy
import com.ai.challenge.core.ContextCompressor
import com.ai.challenge.core.ContextManager
import com.ai.challenge.core.SummaryRepository
import com.ai.challenge.summary.repository.ExposedSummaryRepository
import com.ai.challenge.summary.repository.createSummaryDatabase
```

Add new bindings inside the `module { }` block, after existing repository bindings:

```kotlin
single<SummaryRepository> { ExposedSummaryRepository(createSummaryDatabase()) }
single<CompressionStrategy> { TurnCountStrategy(maxTurns = 15, retainLast = 5) }
single<ContextCompressor> { LlmContextCompressor(service = get(), model = "google/gemini-2.0-flash-001") }
single<ContextManager> { DefaultContextManager(strategy = get(), compressor = get(), summaryRepository = get()) }
```

Update the Agent binding to include `contextManager`:

```kotlin
single<Agent> {
    AiAgent(
        service = get(),
        model = "google/gemini-2.0-flash-001",
        sessionRepository = get(),
        turnRepository = get(),
        tokenRepository = get(),
        costRepository = get(),
        contextManager = get(),
    )
}
```

- [ ] **Step 3: Verify full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 4: Commit**

```bash
git add modules/presentation/app/build.gradle.kts \
       modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt
git commit -m "feat(app): wire context compression into DI configuration"
```

---

### Task 10: Full Integration Verification

- [ ] **Step 1: Run all tests**

Run: `./gradlew test`
Expected: All tests pass across all modules

- [ ] **Step 2: Verify app compiles and starts**

Run: `./gradlew :modules:presentation:app:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit any final fixes if needed**

If any fixes were required, commit them with an appropriate message.
