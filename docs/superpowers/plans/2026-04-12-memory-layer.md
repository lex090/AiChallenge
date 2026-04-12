# Memory Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a Memory Layer as a dedicated Bounded Context with registry-based `MemoryService` facade, migrate Fact/Summary into it, consolidate databases, and add a debug panel.

**Architecture:** New `core/memory` package defines ports (`MemoryService`, `MemoryProvider`, `MemoryType`). New `domain/memory-service` module implements the registry and providers. New `data/memory-repository-exposed` module consolidates two old DB modules. Context-manager strategies switch from direct repository access to `MemoryService`. Debug panel via Decompose + MVIKotlin.

**Tech Stack:** Kotlin 2.3.20, Arrow 2.1.2, Exposed 0.61.0, SQLite, Koin 4.1.0, Decompose 3.5.0, MVIKotlin 4.3.0, Compose Multiplatform 1.10.3

---

### Task 1: Create Memory Domain Model in Core

**Files:**
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/memory/MemoryScope.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/memory/MemoryType.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/memory/MemoryProvider.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/memory/FactMemoryProvider.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/memory/SummaryMemoryProvider.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/memory/MemoryService.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/memory/MemorySnapshot.kt`

- [ ] **Step 1: Create `MemoryScope.kt`**

```kotlin
package com.ai.challenge.core.memory

import com.ai.challenge.core.session.AgentSessionId

/**
 * Scope of agent memory — defines the storage boundary.
 * Value Object (E3): immutable, equality by attributes.
 *
 * Each scope variant determines which memories are accessible.
 * Extensible: add new variants for user-level, branch-level memory.
 */
sealed interface MemoryScope {
    /** Memory bound to a specific agent session. */
    data class Session(val sessionId: AgentSessionId) : MemoryScope
}
```

- [ ] **Step 2: Create `MemoryProvider.kt`**

```kotlin
package com.ai.challenge.core.memory

/**
 * Provider for a specific memory type (E6: Domain Service).
 *
 * Stateless, domain-named operations. Base interface covers
 * read and cleanup. Write operations are on specific sub-interfaces
 * because different memory types have different write semantics
 * (replace-all for facts, append-only for summaries).
 */
interface MemoryProvider<T> {
    suspend fun get(scope: MemoryScope): T
    suspend fun clear(scope: MemoryScope)
}
```

- [ ] **Step 3: Create `FactMemoryProvider.kt`**

```kotlin
package com.ai.challenge.core.memory

import com.ai.challenge.core.fact.Fact

/**
 * Fact memory provider — replace-all write semantics.
 *
 * Invariant: [replace] deletes all existing facts for the scope
 * and writes the new list atomically.
 */
interface FactMemoryProvider : MemoryProvider<List<Fact>> {
    suspend fun replace(scope: MemoryScope, facts: List<Fact>)
}
```

- [ ] **Step 4: Create `SummaryMemoryProvider.kt`**

```kotlin
package com.ai.challenge.core.memory

import com.ai.challenge.core.summary.Summary

/**
 * Summary memory provider — append-only write semantics.
 *
 * Invariant: [append] adds a new summary without touching existing ones.
 * [delete] removes a specific summary from the scope.
 */
interface SummaryMemoryProvider : MemoryProvider<List<Summary>> {
    suspend fun append(scope: MemoryScope, summary: Summary)
    suspend fun delete(scope: MemoryScope, summary: Summary)
}
```

- [ ] **Step 5: Create `MemoryType.kt`**

```kotlin
package com.ai.challenge.core.memory

/**
 * Memory type — type-safe key for provider registry lookup.
 *
 * Sealed interface with phantom type parameter [P] carrying
 * the concrete provider type. Enables compile-time safety:
 * `memoryService.provider(MemoryType.Facts)` returns `FactMemoryProvider`.
 */
sealed interface MemoryType<P : MemoryProvider<*>> {
    /** Extracted facts (replace-all semantics). */
    data object Facts : MemoryType<FactMemoryProvider>
    /** Compressed summaries (append-only semantics). */
    data object Summaries : MemoryType<SummaryMemoryProvider>
}
```

- [ ] **Step 6: Create `MemoryService.kt`**

```kotlin
package com.ai.challenge.core.memory

/**
 * Port for accessing agent memory (E6: Domain Service).
 *
 * Registry-based facade: type-safe provider lookup by [MemoryType],
 * scope-wide lifecycle management via [clearScope].
 *
 * Defined in core, implemented in domain/memory-service.
 */
interface MemoryService {
    fun <P : MemoryProvider<*>> provider(type: MemoryType<P>): P
    suspend fun clearScope(scope: MemoryScope)
}
```

- [ ] **Step 7: Create `MemorySnapshot.kt`**

```kotlin
package com.ai.challenge.core.memory

import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.summary.Summary

/**
 * Immutable snapshot of all memory types in a scope.
 * Value Object (E3): equality by attributes.
 *
 * Used by [GetMemoryUseCase] to return all memory at once.
 */
data class MemorySnapshot(
    val facts: List<Fact>,
    val summaries: List<Summary>,
)
```

- [ ] **Step 8: Verify compilation**

Run: `./gradlew :modules:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/memory/
git commit -m "feat(core): add Memory domain model — MemoryScope, MemoryType, MemoryProvider, MemoryService ports"
```

---

### Task 2: Create Memory Use Cases in Core

**Files:**
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/memory/usecase/GetMemoryUseCase.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/memory/usecase/UpdateFactsUseCase.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/memory/usecase/AddSummaryUseCase.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/memory/usecase/DeleteSummaryUseCase.kt`

- [ ] **Step 1: Create `GetMemoryUseCase.kt`**

```kotlin
package com.ai.challenge.core.memory.usecase

import com.ai.challenge.core.memory.MemorySnapshot
import com.ai.challenge.core.session.AgentSessionId

/**
 * Get all agent memory for a session.
 * Application Use Case: orchestration, no business logic.
 */
interface GetMemoryUseCase {
    suspend fun execute(sessionId: AgentSessionId): MemorySnapshot
}
```

- [ ] **Step 2: Create `UpdateFactsUseCase.kt`**

```kotlin
package com.ai.challenge.core.memory.usecase

import arrow.core.Either
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.session.AgentSessionId

/**
 * Replace all facts for a session (replace-all semantics).
 * Application Use Case: delegates to FactMemoryProvider.replace().
 */
interface UpdateFactsUseCase {
    suspend fun execute(sessionId: AgentSessionId, facts: List<Fact>): Either<DomainError, Unit>
}
```

- [ ] **Step 3: Create `AddSummaryUseCase.kt`**

```kotlin
package com.ai.challenge.core.memory.usecase

import arrow.core.Either
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.summary.Summary

/**
 * Append a summary to a session (append-only semantics).
 * Application Use Case: delegates to SummaryMemoryProvider.append().
 */
interface AddSummaryUseCase {
    suspend fun execute(summary: Summary): Either<DomainError, Unit>
}
```

- [ ] **Step 4: Create `DeleteSummaryUseCase.kt`**

```kotlin
package com.ai.challenge.core.memory.usecase

import arrow.core.Either
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.summary.Summary

/**
 * Delete a specific summary from a session.
 * Application Use Case: delegates to SummaryMemoryProvider.delete().
 */
interface DeleteSummaryUseCase {
    suspend fun execute(summary: Summary): Either<DomainError, Unit>
}
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :modules:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/memory/usecase/
git commit -m "feat(core): add Memory use case interfaces — GetMemory, UpdateFacts, AddSummary, DeleteSummary"
```

---

### Task 3: Create `memory-repository-exposed` Module

**Files:**
- Create: `modules/data/memory-repository-exposed/build.gradle.kts`
- Create: `modules/data/memory-repository-exposed/src/main/kotlin/com/ai/challenge/memory/repository/MemoryDatabase.kt`
- Create: `modules/data/memory-repository-exposed/src/main/kotlin/com/ai/challenge/memory/repository/FactsTable.kt`
- Create: `modules/data/memory-repository-exposed/src/main/kotlin/com/ai/challenge/memory/repository/SummariesTable.kt`
- Create: `modules/data/memory-repository-exposed/src/main/kotlin/com/ai/challenge/memory/repository/ExposedFactRepository.kt`
- Create: `modules/data/memory-repository-exposed/src/main/kotlin/com/ai/challenge/memory/repository/ExposedSummaryRepository.kt`
- Modify: `settings.gradle.kts` — add new module, keep old modules for now
- Test: `modules/data/memory-repository-exposed/src/test/kotlin/com/ai/challenge/memory/repository/ExposedFactRepositoryTest.kt`
- Test: `modules/data/memory-repository-exposed/src/test/kotlin/com/ai/challenge/memory/repository/ExposedSummaryRepositoryTest.kt`

- [ ] **Step 1: Add module to `settings.gradle.kts`**

Add to `settings.gradle.kts` after the existing data module lines:

```kotlin
include(":modules:data:memory-repository-exposed")
```

- [ ] **Step 2: Create `build.gradle.kts`**

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

- [ ] **Step 3: Create `MemoryDatabase.kt`**

```kotlin
package com.ai.challenge.memory.repository

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

fun createMemoryDatabase(): Database {
    val dbDir = Path(System.getProperty("user.home"), ".ai-challenge")
    dbDir.createDirectories()
    val dbPath = dbDir.resolve("memory.db")
    val db = Database.connect(
        url = "jdbc:sqlite:$dbPath",
        driver = "org.sqlite.JDBC",
        setupConnection = { conn ->
            conn.createStatement().execute("PRAGMA foreign_keys = ON")
        },
    )
    transaction(db) {
        SchemaUtils.createMissingTablesAndColumns(FactsTable, SummariesTable)
    }
    migrateFromLegacyDatabases(targetDb = db, dbDir = dbDir)
    return db
}

private fun migrateFromLegacyDatabases(targetDb: Database, dbDir: java.nio.file.Path) {
    val factsDbPath = dbDir.resolve("facts.db")
    val summariesDbPath = dbDir.resolve("sessions.db")

    if (factsDbPath.exists()) {
        migrateFactsFromLegacy(targetDb = targetDb, legacyDbPath = factsDbPath)
    }
    if (summariesDbPath.exists()) {
        migrateSummariesFromLegacy(targetDb = targetDb, legacyDbPath = summariesDbPath)
    }
}

private fun migrateFactsFromLegacy(targetDb: Database, legacyDbPath: java.nio.file.Path) {
    val legacyDb = Database.connect(
        url = "jdbc:sqlite:$legacyDbPath",
        driver = "org.sqlite.JDBC",
    )
    val existingFacts = transaction(legacyDb) {
        try {
            exec("SELECT session_id, category, key, value FROM facts") { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            LegacyFact(
                                sessionId = rs.getString("session_id"),
                                category = rs.getString("category"),
                                key = rs.getString("key"),
                                value = rs.getString("value"),
                            ),
                        )
                    }
                }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
    if (existingFacts.isNotEmpty()) {
        transaction(targetDb) {
            for (fact in existingFacts) {
                exec(
                    "INSERT OR IGNORE INTO facts (session_id, category, key, value) VALUES ('${fact.sessionId}', '${fact.category}', '${fact.key}', '${fact.value}')",
                )
            }
        }
    }
    legacyDbPath.toFile().delete()
}

private fun migrateSummariesFromLegacy(targetDb: Database, legacyDbPath: java.nio.file.Path) {
    val legacyDb = Database.connect(
        url = "jdbc:sqlite:$legacyDbPath",
        driver = "org.sqlite.JDBC",
    )
    val existingSummaries = transaction(legacyDb) {
        try {
            exec("SELECT session_id, text, from_turn_index, to_turn_index, created_at FROM summaries") { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            LegacySummary(
                                sessionId = rs.getString("session_id"),
                                text = rs.getString("text"),
                                fromTurnIndex = rs.getInt("from_turn_index"),
                                toTurnIndex = rs.getInt("to_turn_index"),
                                createdAt = rs.getLong("created_at"),
                            ),
                        )
                    }
                }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
    if (existingSummaries.isNotEmpty()) {
        transaction(targetDb) {
            for (summary in existingSummaries) {
                exec(
                    "INSERT OR IGNORE INTO summaries (session_id, text, from_turn_index, to_turn_index, created_at) VALUES ('${summary.sessionId}', '${summary.text}', ${summary.fromTurnIndex}, ${summary.toTurnIndex}, ${summary.createdAt})",
                )
            }
        }
    }
}

private data class LegacyFact(
    val sessionId: String,
    val category: String,
    val key: String,
    val value: String,
)

private data class LegacySummary(
    val sessionId: String,
    val text: String,
    val fromTurnIndex: Int,
    val toTurnIndex: Int,
    val createdAt: Long,
)
```

- [ ] **Step 4: Create `FactsTable.kt`**

```kotlin
package com.ai.challenge.memory.repository

import org.jetbrains.exposed.sql.Table

object FactsTable : Table("facts") {
    val id = integer("id").autoIncrement()
    val sessionId = varchar("session_id", 36)
    val category = varchar("category", 50)
    val key = varchar("key", 255)
    val value = text("value")

    override val primaryKey = PrimaryKey(id)
}
```

- [ ] **Step 5: Create `SummariesTable.kt`**

```kotlin
package com.ai.challenge.memory.repository

import org.jetbrains.exposed.sql.Table

object SummariesTable : Table("summaries") {
    val id = integer("id").autoIncrement()
    val sessionId = varchar("session_id", 36)
    val text = text("text")
    val fromTurnIndex = integer("from_turn_index")
    val toTurnIndex = integer("to_turn_index")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
```

- [ ] **Step 6: Create `ExposedFactRepository.kt`**

```kotlin
package com.ai.challenge.memory.repository

import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.session.AgentSessionId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedFactRepository(private val database: Database) : FactRepository {

    override suspend fun save(sessionId: AgentSessionId, facts: List<Fact>) {
        transaction(database) {
            FactsTable.deleteWhere { FactsTable.sessionId eq sessionId.value }
            FactsTable.batchInsert(facts) { fact ->
                this[FactsTable.sessionId] = fact.sessionId.value
                this[FactsTable.category] = fact.category.toStorageString()
                this[FactsTable.key] = fact.key.value
                this[FactsTable.value] = fact.value.value
            }
        }
    }

    override suspend fun getBySession(sessionId: AgentSessionId): List<Fact> =
        transaction(database) {
            FactsTable.selectAll()
                .where { FactsTable.sessionId eq sessionId.value }
                .map { it.toFact() }
        }

    override suspend fun deleteBySession(sessionId: AgentSessionId) {
        transaction(database) {
            FactsTable.deleteWhere { FactsTable.sessionId eq sessionId.value }
        }
    }

    private fun ResultRow.toFact() = Fact(
        sessionId = AgentSessionId(value = this[FactsTable.sessionId]),
        category = this[FactsTable.category].toFactCategory(),
        key = FactKey(value = this[FactsTable.key]),
        value = FactValue(value = this[FactsTable.value]),
    )
}

private fun FactCategory.toStorageString(): String = when (this) {
    FactCategory.Goal -> "goal"
    FactCategory.Constraint -> "constraint"
    FactCategory.Preference -> "preference"
    FactCategory.Decision -> "decision"
    FactCategory.Agreement -> "agreement"
}

private fun String.toFactCategory(): FactCategory = when (this) {
    "goal" -> FactCategory.Goal
    "constraint" -> FactCategory.Constraint
    "preference" -> FactCategory.Preference
    "decision" -> FactCategory.Decision
    "agreement" -> FactCategory.Agreement
    else -> error("Unknown fact category: $this")
}
```

- [ ] **Step 7: Create `ExposedSummaryRepository.kt`**

```kotlin
package com.ai.challenge.memory.repository

import com.ai.challenge.core.context.model.SummaryContent
import com.ai.challenge.core.context.model.TurnIndex
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Instant

class ExposedSummaryRepository(private val database: Database) : SummaryRepository {

    override suspend fun save(summary: Summary) {
        transaction(database) {
            SummariesTable.insert {
                it[sessionId] = summary.sessionId.value
                it[text] = summary.content.value
                it[fromTurnIndex] = summary.fromTurnIndex.value
                it[toTurnIndex] = summary.toTurnIndex.value
                it[createdAt] = summary.createdAt.value.toEpochMilliseconds()
            }
        }
    }

    override suspend fun getBySession(sessionId: AgentSessionId): List<Summary> = transaction(database) {
        SummariesTable.selectAll()
            .where { SummariesTable.sessionId eq sessionId.value }
            .map { it.toSummary() }
    }

    override suspend fun deleteBySession(sessionId: AgentSessionId) {
        transaction(database) {
            SummariesTable.deleteWhere { SummariesTable.sessionId eq sessionId.value }
        }
    }

    fun deleteSummary(sessionId: AgentSessionId, fromTurnIndex: Int, toTurnIndex: Int, createdAtMillis: Long) {
        transaction(database) {
            SummariesTable.deleteWhere {
                (SummariesTable.sessionId eq sessionId.value) and
                    (SummariesTable.fromTurnIndex eq fromTurnIndex) and
                    (SummariesTable.toTurnIndex eq toTurnIndex) and
                    (SummariesTable.createdAt eq createdAtMillis)
            }
        }
    }

    private fun ResultRow.toSummary() = Summary(
        sessionId = AgentSessionId(value = this[SummariesTable.sessionId]),
        content = SummaryContent(value = this[SummariesTable.text]),
        fromTurnIndex = TurnIndex(value = this[SummariesTable.fromTurnIndex]),
        toTurnIndex = TurnIndex(value = this[SummariesTable.toTurnIndex]),
        createdAt = CreatedAt(value = Instant.fromEpochMilliseconds(this[SummariesTable.createdAt])),
    )
}
```

- [ ] **Step 8: Write tests for `ExposedFactRepository`**

Create `modules/data/memory-repository-exposed/src/test/kotlin/com/ai/challenge/memory/repository/ExposedFactRepositoryTest.kt`:

```kotlin
package com.ai.challenge.memory.repository

import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.session.AgentSessionId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExposedFactRepositoryTest {

    private lateinit var repo: ExposedFactRepository

    @BeforeTest
    fun setup() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_memory_fact_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(FactsTable)
        }
        repo = ExposedFactRepository(database = db)
    }

    @Test
    fun `save and retrieve facts by session`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val facts = listOf(
            Fact(sessionId = sessionId, category = FactCategory.Goal, key = FactKey(value = "main_goal"), value = FactValue(value = "Build a chat bot")),
            Fact(sessionId = sessionId, category = FactCategory.Constraint, key = FactKey(value = "language"), value = FactValue(value = "Kotlin only")),
        )

        repo.save(sessionId = sessionId, facts = facts)
        val result = repo.getBySession(sessionId = sessionId)

        assertEquals(expected = 2, actual = result.size)
        assertEquals(expected = "main_goal", actual = result.first { it.category == FactCategory.Goal }.key.value)
    }

    @Test
    fun `save overwrites all previous facts`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        repo.save(sessionId = sessionId, facts = listOf(
            Fact(sessionId = sessionId, category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "Old goal")),
        ))

        repo.save(sessionId = sessionId, facts = listOf(
            Fact(sessionId = sessionId, category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "New goal")),
        ))

        val result = repo.getBySession(sessionId = sessionId)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = "New goal", actual = result[0].value.value)
    }

    @Test
    fun `deleteBySession removes all facts`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        repo.save(sessionId = sessionId, facts = listOf(
            Fact(sessionId = sessionId, category = FactCategory.Goal, key = FactKey(value = "goal"), value = FactValue(value = "A goal")),
        ))

        repo.deleteBySession(sessionId = sessionId)

        assertTrue(actual = repo.getBySession(sessionId = sessionId).isEmpty())
    }

    @Test
    fun `facts from different sessions are isolated`() = runTest {
        val s1 = AgentSessionId(value = "s1")
        val s2 = AgentSessionId(value = "s2")
        repo.save(sessionId = s1, facts = listOf(Fact(sessionId = s1, category = FactCategory.Goal, key = FactKey(value = "g"), value = FactValue(value = "S1"))))
        repo.save(sessionId = s2, facts = listOf(Fact(sessionId = s2, category = FactCategory.Goal, key = FactKey(value = "g"), value = FactValue(value = "S2"))))

        assertEquals(expected = "S1", actual = repo.getBySession(sessionId = s1)[0].value.value)
        assertEquals(expected = "S2", actual = repo.getBySession(sessionId = s2)[0].value.value)
    }
}
```

- [ ] **Step 9: Write tests for `ExposedSummaryRepository`**

Create `modules/data/memory-repository-exposed/src/test/kotlin/com/ai/challenge/memory/repository/ExposedSummaryRepositoryTest.kt`:

```kotlin
package com.ai.challenge.memory.repository

import com.ai.challenge.core.context.model.SummaryContent
import com.ai.challenge.core.context.model.TurnIndex
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.summary.Summary
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class ExposedSummaryRepositoryTest {

    private lateinit var repo: ExposedSummaryRepository

    @BeforeTest
    fun setup() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_memory_summary_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(SummariesTable)
        }
        repo = ExposedSummaryRepository(database = db)
    }

    @Test
    fun `save and retrieve summary by session`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val summary = Summary(
            sessionId = sessionId,
            content = SummaryContent(value = "test summary"),
            fromTurnIndex = TurnIndex(value = 0),
            toTurnIndex = TurnIndex(value = 5),
            createdAt = CreatedAt(value = Clock.System.now()),
        )

        repo.save(summary = summary)
        val result = repo.getBySession(sessionId = sessionId)

        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = "test summary", actual = result[0].content.value)
    }

    @Test
    fun `multiple summaries for same session`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        repo.save(summary = Summary(sessionId = sessionId, content = SummaryContent(value = "s1"), fromTurnIndex = TurnIndex(value = 0), toTurnIndex = TurnIndex(value = 3), createdAt = CreatedAt(value = Clock.System.now())))
        repo.save(summary = Summary(sessionId = sessionId, content = SummaryContent(value = "s2"), fromTurnIndex = TurnIndex(value = 0), toTurnIndex = TurnIndex(value = 5), createdAt = CreatedAt(value = Clock.System.now())))

        assertEquals(expected = 2, actual = repo.getBySession(sessionId = sessionId).size)
    }

    @Test
    fun `deleteBySession removes all summaries`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        repo.save(summary = Summary(sessionId = sessionId, content = SummaryContent(value = "s"), fromTurnIndex = TurnIndex(value = 0), toTurnIndex = TurnIndex(value = 3), createdAt = CreatedAt(value = Clock.System.now())))

        repo.deleteBySession(sessionId = sessionId)

        assertTrue(actual = repo.getBySession(sessionId = sessionId).isEmpty())
    }

    @Test
    fun `deleteSummary removes specific summary`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val now = Clock.System.now()
        repo.save(summary = Summary(sessionId = sessionId, content = SummaryContent(value = "keep"), fromTurnIndex = TurnIndex(value = 0), toTurnIndex = TurnIndex(value = 3), createdAt = CreatedAt(value = now)))
        repo.save(summary = Summary(sessionId = sessionId, content = SummaryContent(value = "remove"), fromTurnIndex = TurnIndex(value = 0), toTurnIndex = TurnIndex(value = 5), createdAt = CreatedAt(value = now)))

        repo.deleteSummary(sessionId = sessionId, fromTurnIndex = 0, toTurnIndex = 5, createdAtMillis = now.toEpochMilliseconds())

        val result = repo.getBySession(sessionId = sessionId)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = "keep", actual = result[0].content.value)
    }
}
```

- [ ] **Step 10: Run tests**

Run: `./gradlew :modules:data:memory-repository-exposed:test`
Expected: All 8 tests PASS

- [ ] **Step 11: Commit**

```bash
git add modules/data/memory-repository-exposed/ settings.gradle.kts
git commit -m "feat(data): add memory-repository-exposed module with unified memory.db"
```

---

### Task 4: Create `memory-service` Domain Module

**Files:**
- Create: `modules/domain/memory-service/build.gradle.kts`
- Create: `modules/domain/memory-service/src/main/kotlin/com/ai/challenge/memory/service/DefaultMemoryService.kt`
- Create: `modules/domain/memory-service/src/main/kotlin/com/ai/challenge/memory/service/provider/DefaultFactMemoryProvider.kt`
- Create: `modules/domain/memory-service/src/main/kotlin/com/ai/challenge/memory/service/provider/DefaultSummaryMemoryProvider.kt`
- Create: `modules/domain/memory-service/src/main/kotlin/com/ai/challenge/memory/service/handler/SessionDeletedCleanupHandler.kt`
- Create: `modules/domain/memory-service/src/main/kotlin/com/ai/challenge/memory/service/usecase/DefaultGetMemoryUseCase.kt`
- Create: `modules/domain/memory-service/src/main/kotlin/com/ai/challenge/memory/service/usecase/DefaultUpdateFactsUseCase.kt`
- Create: `modules/domain/memory-service/src/main/kotlin/com/ai/challenge/memory/service/usecase/DefaultAddSummaryUseCase.kt`
- Create: `modules/domain/memory-service/src/main/kotlin/com/ai/challenge/memory/service/usecase/DefaultDeleteSummaryUseCase.kt`
- Modify: `settings.gradle.kts` — add module
- Test: `modules/domain/memory-service/src/test/kotlin/com/ai/challenge/memory/service/DefaultMemoryServiceTest.kt`
- Test: `modules/domain/memory-service/src/test/kotlin/com/ai/challenge/memory/service/handler/SessionDeletedCleanupHandlerTest.kt`

- [ ] **Step 1: Add module to `settings.gradle.kts`**

Add after `include(":modules:domain:context-manager")`:

```kotlin
include(":modules:domain:memory-service")
```

- [ ] **Step 2: Create `build.gradle.kts`**

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
    implementation(libs.arrow.core)

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

- [ ] **Step 3: Create `DefaultFactMemoryProvider.kt`**

```kotlin
package com.ai.challenge.memory.service.provider

import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.memory.FactMemoryProvider
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.session.AgentSessionId

class DefaultFactMemoryProvider(
    private val factRepository: FactRepository,
) : FactMemoryProvider {

    override suspend fun get(scope: MemoryScope): List<Fact> {
        val sessionId = scope.toSessionId()
        return factRepository.getBySession(sessionId = sessionId)
    }

    override suspend fun replace(scope: MemoryScope, facts: List<Fact>) {
        val sessionId = scope.toSessionId()
        if (facts.isEmpty()) {
            factRepository.deleteBySession(sessionId = sessionId)
        } else {
            factRepository.save(sessionId = sessionId, facts = facts)
        }
    }

    override suspend fun clear(scope: MemoryScope) {
        val sessionId = scope.toSessionId()
        factRepository.deleteBySession(sessionId = sessionId)
    }

    private fun MemoryScope.toSessionId(): AgentSessionId = when (this) {
        is MemoryScope.Session -> sessionId
    }
}
```

- [ ] **Step 4: Create `DefaultSummaryMemoryProvider.kt`**

```kotlin
package com.ai.challenge.memory.service.provider

import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.SummaryMemoryProvider
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.memory.repository.ExposedSummaryRepository

class DefaultSummaryMemoryProvider(
    private val summaryRepository: SummaryRepository,
) : SummaryMemoryProvider {

    override suspend fun get(scope: MemoryScope): List<Summary> {
        val sessionId = scope.toSessionId()
        return summaryRepository.getBySession(sessionId = sessionId)
    }

    override suspend fun append(scope: MemoryScope, summary: Summary) {
        summaryRepository.save(summary = summary)
    }

    override suspend fun delete(scope: MemoryScope, summary: Summary) {
        val repo = summaryRepository as ExposedSummaryRepository
        repo.deleteSummary(
            sessionId = summary.sessionId,
            fromTurnIndex = summary.fromTurnIndex.value,
            toTurnIndex = summary.toTurnIndex.value,
            createdAtMillis = summary.createdAt.value.toEpochMilliseconds(),
        )
    }

    override suspend fun clear(scope: MemoryScope) {
        val sessionId = scope.toSessionId()
        summaryRepository.deleteBySession(sessionId = sessionId)
    }

    private fun MemoryScope.toSessionId(): AgentSessionId = when (this) {
        is MemoryScope.Session -> sessionId
    }
}
```

- [ ] **Step 5: Create `DefaultMemoryService.kt`**

```kotlin
package com.ai.challenge.memory.service

import com.ai.challenge.core.memory.FactMemoryProvider
import com.ai.challenge.core.memory.MemoryProvider
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryService
import com.ai.challenge.core.memory.MemoryType
import com.ai.challenge.core.memory.SummaryMemoryProvider

class DefaultMemoryService(
    private val factMemoryProvider: FactMemoryProvider,
    private val summaryMemoryProvider: SummaryMemoryProvider,
) : MemoryService {

    private val providers: Map<MemoryType<*>, MemoryProvider<*>> = mapOf(
        MemoryType.Facts to factMemoryProvider,
        MemoryType.Summaries to summaryMemoryProvider,
    )

    @Suppress("UNCHECKED_CAST")
    override fun <P : MemoryProvider<*>> provider(type: MemoryType<P>): P =
        providers[type] as? P ?: error("No provider registered for memory type: $type")

    override suspend fun clearScope(scope: MemoryScope) {
        for (provider in providers.values) {
            provider.clear(scope = scope)
        }
    }
}
```

- [ ] **Step 6: Create `SessionDeletedCleanupHandler.kt`**

```kotlin
package com.ai.challenge.memory.service.handler

import com.ai.challenge.core.event.DomainEvent
import com.ai.challenge.core.event.DomainEventHandler
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryService

/**
 * Event Handler — cleans up all memory data
 * when a session is deleted in Conversation Context.
 *
 * Delegates to [MemoryService.clearScope] which iterates
 * all registered providers. Adding new memory types
 * requires no changes to this handler.
 */
class SessionDeletedCleanupHandler(
    private val memoryService: MemoryService,
) : DomainEventHandler<DomainEvent.SessionDeleted> {

    override suspend fun handle(event: DomainEvent.SessionDeleted) {
        memoryService.clearScope(scope = MemoryScope.Session(sessionId = event.sessionId))
    }
}
```

- [ ] **Step 7: Create `DefaultGetMemoryUseCase.kt`**

```kotlin
package com.ai.challenge.memory.service.usecase

import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryService
import com.ai.challenge.core.memory.MemorySnapshot
import com.ai.challenge.core.memory.MemoryType
import com.ai.challenge.core.memory.usecase.GetMemoryUseCase
import com.ai.challenge.core.session.AgentSessionId

class DefaultGetMemoryUseCase(
    private val memoryService: MemoryService,
) : GetMemoryUseCase {

    override suspend fun execute(sessionId: AgentSessionId): MemorySnapshot {
        val scope = MemoryScope.Session(sessionId = sessionId)
        val facts = memoryService.provider(type = MemoryType.Facts).get(scope = scope)
        val summaries = memoryService.provider(type = MemoryType.Summaries).get(scope = scope)
        return MemorySnapshot(facts = facts, summaries = summaries)
    }
}
```

- [ ] **Step 8: Create `DefaultUpdateFactsUseCase.kt`**

```kotlin
package com.ai.challenge.memory.service.usecase

import arrow.core.Either
import arrow.core.right
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryService
import com.ai.challenge.core.memory.MemoryType
import com.ai.challenge.core.memory.usecase.UpdateFactsUseCase
import com.ai.challenge.core.session.AgentSessionId

class DefaultUpdateFactsUseCase(
    private val memoryService: MemoryService,
) : UpdateFactsUseCase {

    override suspend fun execute(sessionId: AgentSessionId, facts: List<Fact>): Either<DomainError, Unit> {
        val scope = MemoryScope.Session(sessionId = sessionId)
        memoryService.provider(type = MemoryType.Facts).replace(scope = scope, facts = facts)
        return Unit.right()
    }
}
```

- [ ] **Step 9: Create `DefaultAddSummaryUseCase.kt`**

```kotlin
package com.ai.challenge.memory.service.usecase

import arrow.core.Either
import arrow.core.right
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryService
import com.ai.challenge.core.memory.MemoryType
import com.ai.challenge.core.memory.usecase.AddSummaryUseCase
import com.ai.challenge.core.summary.Summary

class DefaultAddSummaryUseCase(
    private val memoryService: MemoryService,
) : AddSummaryUseCase {

    override suspend fun execute(summary: Summary): Either<DomainError, Unit> {
        val scope = MemoryScope.Session(sessionId = summary.sessionId)
        memoryService.provider(type = MemoryType.Summaries).append(scope = scope, summary = summary)
        return Unit.right()
    }
}
```

- [ ] **Step 10: Create `DefaultDeleteSummaryUseCase.kt`**

```kotlin
package com.ai.challenge.memory.service.usecase

import arrow.core.Either
import arrow.core.right
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryService
import com.ai.challenge.core.memory.MemoryType
import com.ai.challenge.core.memory.usecase.DeleteSummaryUseCase
import com.ai.challenge.core.summary.Summary

class DefaultDeleteSummaryUseCase(
    private val memoryService: MemoryService,
) : DeleteSummaryUseCase {

    override suspend fun execute(summary: Summary): Either<DomainError, Unit> {
        val scope = MemoryScope.Session(sessionId = summary.sessionId)
        memoryService.provider(type = MemoryType.Summaries).delete(scope = scope, summary = summary)
        return Unit.right()
    }
}
```

- [ ] **Step 11: Write test for `DefaultMemoryService`**

Create `modules/domain/memory-service/src/test/kotlin/com/ai/challenge/memory/service/DefaultMemoryServiceTest.kt`:

```kotlin
package com.ai.challenge.memory.service

import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.memory.FactMemoryProvider
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryType
import com.ai.challenge.core.memory.SummaryMemoryProvider
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultMemoryServiceTest {

    @Test
    fun `provider returns typed FactMemoryProvider for Facts type`() {
        val service = createTestService()
        val provider: FactMemoryProvider = service.provider(type = MemoryType.Facts)
        assertTrue(actual = provider is InMemoryFactMemoryProvider)
    }

    @Test
    fun `provider returns typed SummaryMemoryProvider for Summaries type`() {
        val service = createTestService()
        val provider: SummaryMemoryProvider = service.provider(type = MemoryType.Summaries)
        assertTrue(actual = provider is InMemorySummaryMemoryProvider)
    }

    @Test
    fun `clearScope clears all providers`() = runTest {
        val factProvider = InMemoryFactMemoryProvider()
        val summaryProvider = InMemorySummaryMemoryProvider()
        val service = DefaultMemoryService(
            factMemoryProvider = factProvider,
            summaryMemoryProvider = summaryProvider,
        )
        val scope = MemoryScope.Session(sessionId = AgentSessionId(value = "s1"))

        factProvider.replace(scope = scope, facts = listOf(
            Fact(sessionId = AgentSessionId(value = "s1"), category = FactCategory.Goal, key = FactKey(value = "k"), value = FactValue(value = "v")),
        ))

        service.clearScope(scope = scope)

        assertTrue(actual = factProvider.get(scope = scope).isEmpty())
        assertTrue(actual = summaryProvider.get(scope = scope).isEmpty())
    }

    private fun createTestService(): DefaultMemoryService = DefaultMemoryService(
        factMemoryProvider = InMemoryFactMemoryProvider(),
        summaryMemoryProvider = InMemorySummaryMemoryProvider(),
    )
}

internal class InMemoryFactMemoryProvider : FactMemoryProvider {
    private val store = mutableMapOf<AgentSessionId, List<Fact>>()

    override suspend fun get(scope: MemoryScope): List<Fact> = when (scope) {
        is MemoryScope.Session -> store[scope.sessionId] ?: emptyList()
    }

    override suspend fun replace(scope: MemoryScope, facts: List<Fact>) {
        when (scope) {
            is MemoryScope.Session -> store[scope.sessionId] = facts
        }
    }

    override suspend fun clear(scope: MemoryScope) {
        when (scope) {
            is MemoryScope.Session -> store.remove(key = scope.sessionId)
        }
    }
}

internal class InMemorySummaryMemoryProvider : SummaryMemoryProvider {
    private val store = mutableListOf<Summary>()

    override suspend fun get(scope: MemoryScope): List<Summary> = when (scope) {
        is MemoryScope.Session -> store.filter { it.sessionId == scope.sessionId }
    }

    override suspend fun append(scope: MemoryScope, summary: Summary) {
        store.add(element = summary)
    }

    override suspend fun delete(scope: MemoryScope, summary: Summary) {
        store.removeAll { it == summary }
    }

    override suspend fun clear(scope: MemoryScope) {
        when (scope) {
            is MemoryScope.Session -> store.removeAll { it.sessionId == scope.sessionId }
        }
    }
}
```

- [ ] **Step 12: Write test for `SessionDeletedCleanupHandler`**

Create `modules/domain/memory-service/src/test/kotlin/com/ai/challenge/memory/service/handler/SessionDeletedCleanupHandlerTest.kt`:

```kotlin
package com.ai.challenge.memory.service.handler

import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.event.DomainEvent
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryType
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.memory.service.DefaultMemoryService
import com.ai.challenge.memory.service.InMemoryFactMemoryProvider
import com.ai.challenge.memory.service.InMemorySummaryMemoryProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SessionDeletedCleanupHandlerTest {

    @Test
    fun `handle clears all memory for deleted session`() = runTest {
        val factProvider = InMemoryFactMemoryProvider()
        val summaryProvider = InMemorySummaryMemoryProvider()
        val memoryService = DefaultMemoryService(
            factMemoryProvider = factProvider,
            summaryMemoryProvider = summaryProvider,
        )
        val sessionId = AgentSessionId.generate()
        val scope = MemoryScope.Session(sessionId = sessionId)

        factProvider.replace(scope = scope, facts = listOf(
            Fact(sessionId = sessionId, category = FactCategory.Goal, key = FactKey(value = "k"), value = FactValue(value = "v")),
        ))

        val handler = SessionDeletedCleanupHandler(memoryService = memoryService)
        handler.handle(event = DomainEvent.SessionDeleted(sessionId = sessionId))

        assertTrue(actual = factProvider.get(scope = scope).isEmpty())
        assertTrue(actual = summaryProvider.get(scope = scope).isEmpty())
    }

    @Test
    fun `handle with no data does not throw`() = runTest {
        val memoryService = DefaultMemoryService(
            factMemoryProvider = InMemoryFactMemoryProvider(),
            summaryMemoryProvider = InMemorySummaryMemoryProvider(),
        )
        val handler = SessionDeletedCleanupHandler(memoryService = memoryService)
        handler.handle(event = DomainEvent.SessionDeleted(sessionId = AgentSessionId.generate()))
    }
}
```

- [ ] **Step 13: Run tests**

Run: `./gradlew :modules:domain:memory-service:test`
Expected: All 5 tests PASS

- [ ] **Step 14: Commit**

```bash
git add modules/domain/memory-service/ settings.gradle.kts
git commit -m "feat(domain): add memory-service module with DefaultMemoryService, providers, cleanup handler, use cases"
```

---

### Task 5: Migrate Context-Manager to Use MemoryService

**Files:**
- Modify: `modules/domain/context-manager/build.gradle.kts` — remove direct repo dependency if any, keep core
- Modify: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/StickyFactsStrategy.kt`
- Modify: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/SummarizeOnThresholdStrategy.kt`
- Delete: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/SessionDeletedCleanupHandler.kt`
- Modify: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/TestFakes.kt` — add InMemoryMemoryService
- Modify: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/SessionDeletedCleanupHandlerTest.kt` — delete (moved)

- [ ] **Step 1: Update `StickyFactsStrategy.kt`**

Replace the file content with:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.ContextStrategyConfig
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.context.PreparedContext
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryService
import com.ai.challenge.core.memory.MemoryType
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn

class StickyFactsStrategy(
    private val repository: AgentSessionRepository,
    private val memoryService: MemoryService,
    private val factExtractor: FactExtractor,
) : ContextStrategy {

    override suspend fun prepare(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        config: ContextStrategyConfig,
    ): PreparedContext {
        val stickyConfig = config as ContextStrategyConfig.StickyFacts
        val factProvider = memoryService.provider(type = MemoryType.Facts)
        val scope = MemoryScope.Session(sessionId = sessionId)

        val currentFacts = factProvider.get(scope = scope)
        val history = repository.getTurnsByBranch(branchId = branchId)
        val lastAssistantResponse = history.lastOrNull()?.assistantMessage

        val updatedFacts = factExtractor.extract(
            sessionId = sessionId,
            currentFacts = currentFacts,
            newUserMessage = newMessage,
            lastAssistantResponse = lastAssistantResponse,
        )
        if (updatedFacts.isEmpty()) {
            factProvider.clear(scope = scope)
        } else {
            factProvider.replace(scope = scope, facts = updatedFacts)
        }

        val retained = if (history.size > stickyConfig.retainLastTurns) {
            history.subList(history.size - stickyConfig.retainLastTurns, history.size)
        } else {
            history
        }

        return if (updatedFacts.isEmpty()) {
            withoutCompression(history = retained, newMessage = newMessage).copy(
                originalTurnCount = history.size,
            )
        } else {
            withFacts(facts = updatedFacts, retainedTurns = retained, history = history, newMessage = newMessage)
        }
    }

    private fun withoutCompression(history: List<Turn>, newMessage: MessageContent): PreparedContext =
        PreparedContext(
            messages = turnsToMessages(turns = history) + ContextMessage(role = MessageRole.User, content = newMessage),
            compressed = false,
            originalTurnCount = history.size,
            retainedTurnCount = history.size,
            summaryCount = 0,
        )

    private fun withFacts(
        facts: List<Fact>,
        retainedTurns: List<Turn>,
        history: List<Turn>,
        newMessage: MessageContent,
    ): PreparedContext =
        PreparedContext(
            messages = factsMessages(facts = facts, retainedTurns = retainedTurns, newMessage = newMessage),
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = retainedTurns.size,
            summaryCount = 0,
        )

    private fun factsMessages(
        facts: List<Fact>,
        retainedTurns: List<Turn>,
        newMessage: MessageContent,
    ): List<ContextMessage> =
        buildList {
            add(ContextMessage(role = MessageRole.System, content = MessageContent(value = formatFacts(facts = facts))))
            addAll(turnsToMessages(turns = retainedTurns))
            add(ContextMessage(role = MessageRole.User, content = newMessage))
        }

    private fun formatFacts(facts: List<Fact>): String {
        val grouped = facts.groupBy { it.category }
        return buildString {
            appendLine("You have the following context about this conversation:")
            appendLine()
            appendCategoryIfPresent(grouped = grouped, category = FactCategory.Goal, header = "## Goals")
            appendCategoryIfPresent(grouped = grouped, category = FactCategory.Constraint, header = "## Constraints")
            appendCategoryIfPresent(grouped = grouped, category = FactCategory.Preference, header = "## Preferences")
            appendCategoryIfPresent(grouped = grouped, category = FactCategory.Decision, header = "## Decisions")
            appendCategoryIfPresent(grouped = grouped, category = FactCategory.Agreement, header = "## Agreements")
        }.trimEnd()
    }

    private fun StringBuilder.appendCategoryIfPresent(
        grouped: Map<FactCategory, List<Fact>>,
        category: FactCategory,
        header: String,
    ) {
        val categoryFacts = grouped[category] ?: return
        appendLine(header)
        for (fact in categoryFacts) {
            appendLine("- ${fact.key.value}: ${fact.value.value}")
        }
        appendLine()
    }
}
```

- [ ] **Step 2: Update `SummarizeOnThresholdStrategy.kt`**

Replace the file content with:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.ContextStrategyConfig
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.context.PreparedContext
import com.ai.challenge.core.context.model.SummaryContent
import com.ai.challenge.core.context.model.TurnIndex
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryService
import com.ai.challenge.core.memory.MemoryType
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.turn.Turn
import kotlin.time.Clock

class SummarizeOnThresholdStrategy(
    private val repository: AgentSessionRepository,
    private val compressor: ContextCompressor,
    private val memoryService: MemoryService,
) : ContextStrategy {

    override suspend fun prepare(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        config: ContextStrategyConfig,
    ): PreparedContext {
        val summarizeConfig = config as ContextStrategyConfig.SummarizeOnThreshold
        val history = repository.getTurnsByBranch(branchId = branchId)

        if (history.size < summarizeConfig.maxTurnsBeforeCompression) {
            return withoutCompression(history = history, newMessage = newMessage)
        }

        val summaryProvider = memoryService.provider(type = MemoryType.Summaries)
        val scope = MemoryScope.Session(sessionId = sessionId)
        val lastSummary = summaryProvider.get(scope = scope).maxByOrNull { it.toTurnIndex.value }

        if (lastSummary != null) {
            val turnsSinceLastSummary = history.size - lastSummary.toTurnIndex.value
            if (turnsSinceLastSummary < summarizeConfig.retainLastTurns + summarizeConfig.compressionInterval) {
                return withExistingSummary(summary = lastSummary, history = history, newMessage = newMessage)
            }
        }

        val splitAt = (history.size - summarizeConfig.retainLastTurns).coerceAtLeast(minimumValue = 0)
        val summaryContent = compressTurns(history = history, splitAt = splitAt, lastSummary = lastSummary)
        val newSummary = Summary(
            sessionId = sessionId,
            content = summaryContent,
            fromTurnIndex = TurnIndex(value = 0),
            toTurnIndex = TurnIndex(value = splitAt),
            createdAt = CreatedAt(value = Clock.System.now()),
        )
        summaryProvider.append(scope = scope, summary = newSummary)
        return withNewSummary(summaryContent = summaryContent, history = history, splitAt = splitAt, newMessage = newMessage)
    }

    private suspend fun compressTurns(
        history: List<Turn>,
        splitAt: Int,
        lastSummary: Summary?,
    ): SummaryContent = when (lastSummary) {
        null -> compressor.compress(
            turns = history.subList(0, splitAt),
            previousSummary = null,
        )
        else -> compressor.compress(
            turns = history.subList(lastSummary.toTurnIndex.value, splitAt),
            previousSummary = lastSummary,
        )
    }

    private fun withoutCompression(history: List<Turn>, newMessage: MessageContent): PreparedContext =
        PreparedContext(
            messages = turnsToMessages(turns = history) + ContextMessage(role = MessageRole.User, content = newMessage),
            compressed = false,
            originalTurnCount = history.size,
            retainedTurnCount = history.size,
            summaryCount = 0,
        )

    private fun withExistingSummary(
        summary: Summary,
        history: List<Turn>,
        newMessage: MessageContent,
    ): PreparedContext {
        val retained = history.subList(summary.toTurnIndex.value, history.size)
        return PreparedContext(
            messages = summarizedMessages(
                summaryText = summary.content.value,
                retainedTurns = retained,
                newMessage = newMessage,
            ),
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = retained.size,
            summaryCount = 1,
        )
    }

    private fun withNewSummary(
        summaryContent: SummaryContent,
        history: List<Turn>,
        splitAt: Int,
        newMessage: MessageContent,
    ): PreparedContext {
        val retained = history.subList(splitAt, history.size)
        return PreparedContext(
            messages = summarizedMessages(summaryText = summaryContent.value, retainedTurns = retained, newMessage = newMessage),
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = retained.size,
            summaryCount = 1,
        )
    }

    private fun summarizedMessages(
        summaryText: String,
        retainedTurns: List<Turn>,
        newMessage: MessageContent,
    ): List<ContextMessage> =
        buildList {
            add(ContextMessage(role = MessageRole.System, content = MessageContent(value = "Previous conversation summary:\n$summaryText")))
            addAll(turnsToMessages(turns = retainedTurns))
            add(ContextMessage(role = MessageRole.User, content = newMessage))
        }
}
```

- [ ] **Step 3: Delete old `SessionDeletedCleanupHandler.kt` from context-manager**

Delete: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/SessionDeletedCleanupHandler.kt`

- [ ] **Step 4: Delete old `SessionDeletedCleanupHandlerTest.kt` from context-manager**

Delete: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/SessionDeletedCleanupHandlerTest.kt`

- [ ] **Step 5: Update `TestFakes.kt` — remove `InMemoryFactRepository` and `InMemorySummaryRepository`, add in-memory MemoryService**

In `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/TestFakes.kt`, remove the `InMemoryFactRepository` class (lines 133-146) and `InMemorySummaryRepository` class (lines 148-161). Add:

```kotlin
import com.ai.challenge.core.memory.FactMemoryProvider
import com.ai.challenge.core.memory.MemoryProvider
import com.ai.challenge.core.memory.MemoryScope
import com.ai.challenge.core.memory.MemoryService
import com.ai.challenge.core.memory.MemoryType
import com.ai.challenge.core.memory.SummaryMemoryProvider

internal class InMemoryFactMemoryProvider : FactMemoryProvider {
    private val store = mutableMapOf<AgentSessionId, List<Fact>>()

    override suspend fun get(scope: MemoryScope): List<Fact> = when (scope) {
        is MemoryScope.Session -> store[scope.sessionId] ?: emptyList()
    }

    override suspend fun replace(scope: MemoryScope, facts: List<Fact>) {
        when (scope) {
            is MemoryScope.Session -> store[scope.sessionId] = facts
        }
    }

    override suspend fun clear(scope: MemoryScope) {
        when (scope) {
            is MemoryScope.Session -> store.remove(key = scope.sessionId)
        }
    }
}

internal class InMemorySummaryMemoryProvider : SummaryMemoryProvider {
    private val store = mutableListOf<Summary>()

    override suspend fun get(scope: MemoryScope): List<Summary> = when (scope) {
        is MemoryScope.Session -> store.filter { it.sessionId == scope.sessionId }
    }

    override suspend fun append(scope: MemoryScope, summary: Summary) {
        store.add(element = summary)
    }

    override suspend fun delete(scope: MemoryScope, summary: Summary) {
        store.removeAll { it == summary }
    }

    override suspend fun clear(scope: MemoryScope) {
        when (scope) {
            is MemoryScope.Session -> store.removeAll { it.sessionId == scope.sessionId }
        }
    }
}

internal class InMemoryMemoryService(
    private val factProvider: InMemoryFactMemoryProvider = InMemoryFactMemoryProvider(),
    private val summaryProvider: InMemorySummaryMemoryProvider = InMemorySummaryMemoryProvider(),
) : MemoryService {
    private val providers: Map<MemoryType<*>, MemoryProvider<*>> = mapOf(
        MemoryType.Facts to factProvider,
        MemoryType.Summaries to summaryProvider,
    )

    @Suppress("UNCHECKED_CAST")
    override fun <P : MemoryProvider<*>> provider(type: MemoryType<P>): P =
        providers[type] as P

    override suspend fun clearScope(scope: MemoryScope) {
        for (provider in providers.values) {
            provider.clear(scope = scope)
        }
    }
}
```

- [ ] **Step 6: Update existing context-manager tests that reference `InMemoryFactRepository`/`InMemorySummaryRepository`**

Search all test files in `modules/domain/context-manager/src/test/` for references to `InMemoryFactRepository` and `InMemorySummaryRepository` and update them to use `InMemoryMemoryService`. This includes updating any strategy tests that construct `StickyFactsStrategy` or `SummarizeOnThresholdStrategy` to pass `memoryService` instead of repository.

- [ ] **Step 7: Verify compilation and tests**

Run: `./gradlew :modules:domain:context-manager:compileKotlin`
Expected: BUILD SUCCESSFUL

Run: `./gradlew :modules:domain:context-manager:test`
Expected: All tests PASS

- [ ] **Step 8: Commit**

```bash
git add modules/domain/context-manager/
git commit -m "refactor(context-manager): migrate strategies to MemoryService, remove old cleanup handler"
```

---

### Task 6: Update DI Configuration (AppModule)

**Files:**
- Modify: `modules/presentation/app/build.gradle.kts` — add memory-service, memory-repository-exposed; remove old fact/summary modules
- Modify: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt`

- [ ] **Step 1: Update `build.gradle.kts`**

In `modules/presentation/app/build.gradle.kts`, replace:

```kotlin
    implementation(project(":modules:data:summary-repository-exposed"))
    implementation(project(":modules:data:fact-repository-exposed"))
```

with:

```kotlin
    implementation(project(":modules:data:memory-repository-exposed"))
    implementation(project(":modules:domain:memory-service"))
```

- [ ] **Step 2: Update `AppModule.kt`**

Replace imports and DI bindings. Key changes:
- Remove `FactRepository` and `SummaryRepository` direct bindings
- Add `MemoryService`, `FactMemoryProvider`, `SummaryMemoryProvider` bindings
- Add memory use case bindings
- Update strategy constructors to use `memoryService`
- Update cleanup handler to use `memoryService`

Full updated `AppModule.kt`:

```kotlin
package com.ai.challenge.app.di

import com.ai.challenge.agent.AiBranchService
import com.ai.challenge.agent.AiChatService
import com.ai.challenge.agent.AiSessionService
import com.ai.challenge.agent.AiUsageQueryService
import com.ai.challenge.context.BranchingContextManager
import com.ai.challenge.context.ContextCompressor
import com.ai.challenge.context.ContextPreparationService
import com.ai.challenge.context.ContextStrategy
import com.ai.challenge.context.FactExtractor
import com.ai.challenge.context.LlmContextCompressor
import com.ai.challenge.context.LlmFactExtractor
import com.ai.challenge.context.PassthroughStrategy
import com.ai.challenge.context.SlidingWindowStrategy
import com.ai.challenge.context.StickyFactsStrategy
import com.ai.challenge.context.SummarizeOnThresholdStrategy
import com.ai.challenge.core.chat.BranchService
import com.ai.challenge.core.chat.ChatService
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.ContextStrategyConfig
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.llm.LlmPort
import com.ai.challenge.core.memory.FactMemoryProvider
import com.ai.challenge.core.memory.MemoryService
import com.ai.challenge.core.memory.SummaryMemoryProvider
import com.ai.challenge.core.memory.usecase.AddSummaryUseCase
import com.ai.challenge.core.memory.usecase.DeleteSummaryUseCase
import com.ai.challenge.core.memory.usecase.GetMemoryUseCase
import com.ai.challenge.core.memory.usecase.UpdateFactsUseCase
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.event.DomainEvent
import com.ai.challenge.core.event.DomainEventPublisher
import com.ai.challenge.core.usage.UsageQueryService
import com.ai.challenge.core.usecase.ApplicationInitService
import com.ai.challenge.core.usecase.CreateSessionUseCase
import com.ai.challenge.core.usecase.DeleteSessionUseCase
import com.ai.challenge.core.usecase.SendMessageUseCase
import com.ai.challenge.app.event.InProcessDomainEventPublisher
import com.ai.challenge.llm.OpenRouterAdapter
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.memory.repository.ExposedFactRepository
import com.ai.challenge.memory.repository.ExposedSummaryRepository
import com.ai.challenge.memory.repository.createMemoryDatabase
import com.ai.challenge.memory.service.DefaultMemoryService
import com.ai.challenge.memory.service.handler.SessionDeletedCleanupHandler
import com.ai.challenge.memory.service.provider.DefaultFactMemoryProvider
import com.ai.challenge.memory.service.provider.DefaultSummaryMemoryProvider
import com.ai.challenge.memory.service.usecase.DefaultAddSummaryUseCase
import com.ai.challenge.memory.service.usecase.DefaultDeleteSummaryUseCase
import com.ai.challenge.memory.service.usecase.DefaultGetMemoryUseCase
import com.ai.challenge.memory.service.usecase.DefaultUpdateFactsUseCase
import com.ai.challenge.session.repository.ExposedAgentSessionRepository
import com.ai.challenge.session.repository.createSessionDatabase
import org.koin.dsl.module

val appModule = module {
    single {
        OpenRouterService(
            apiKey = System.getenv("OPENROUTER_API_KEY")
                ?: error("OPENROUTER_API_KEY environment variable is not set"),
        )
    }

    single<LlmPort> {
        OpenRouterAdapter(
            openRouterService = get(),
            model = "google/gemini-2.0-flash-001",
        )
    }

    // Repositories
    single<AgentSessionRepository> { ExposedAgentSessionRepository(database = createSessionDatabase()) }

    // Memory Layer
    single { createMemoryDatabase() }
    single<FactRepository> { ExposedFactRepository(database = get()) }
    single<SummaryRepository> { ExposedSummaryRepository(database = get()) }
    single<FactMemoryProvider> { DefaultFactMemoryProvider(factRepository = get()) }
    single<SummaryMemoryProvider> { DefaultSummaryMemoryProvider(summaryRepository = get()) }
    single<MemoryService> { DefaultMemoryService(factMemoryProvider = get(), summaryMemoryProvider = get()) }

    // Memory Use Cases
    single<GetMemoryUseCase> { DefaultGetMemoryUseCase(memoryService = get()) }
    single<UpdateFactsUseCase> { DefaultUpdateFactsUseCase(memoryService = get()) }
    single<AddSummaryUseCase> { DefaultAddSummaryUseCase(memoryService = get()) }
    single<DeleteSummaryUseCase> { DefaultDeleteSummaryUseCase(memoryService = get()) }

    // Context management
    single<ContextCompressor> { LlmContextCompressor(llmPort = get()) }
    single<FactExtractor> { LlmFactExtractor(llmPort = get()) }

    // Context strategies
    single { PassthroughStrategy(repository = get()) }
    single { SlidingWindowStrategy(repository = get()) }
    single {
        SummarizeOnThresholdStrategy(
            repository = get(),
            compressor = get(),
            memoryService = get(),
        )
    }
    single {
        StickyFactsStrategy(
            repository = get(),
            memoryService = get(),
            factExtractor = get(),
        )
    }
    single { BranchingContextManager(repository = get()) }

    single<ContextManager> {
        ContextPreparationService(
            strategies = mapOf(
                ContextManagementType.None to get<PassthroughStrategy>() as ContextStrategy,
                ContextManagementType.SummarizeOnThreshold to get<SummarizeOnThresholdStrategy>() as ContextStrategy,
                ContextManagementType.SlidingWindow to get<SlidingWindowStrategy>() as ContextStrategy,
                ContextManagementType.StickyFacts to get<StickyFactsStrategy>() as ContextStrategy,
                ContextManagementType.Branching to get<BranchingContextManager>() as ContextStrategy,
            ),
            configs = mapOf(
                ContextManagementType.None to ContextStrategyConfig.None as ContextStrategyConfig,
                ContextManagementType.SummarizeOnThreshold to ContextStrategyConfig.SummarizeOnThreshold(
                    maxTurnsBeforeCompression = 15,
                    retainLastTurns = 5,
                    compressionInterval = 10,
                ) as ContextStrategyConfig,
                ContextManagementType.SlidingWindow to ContextStrategyConfig.SlidingWindow(
                    windowSize = 10,
                ) as ContextStrategyConfig,
                ContextManagementType.StickyFacts to ContextStrategyConfig.StickyFacts(
                    retainLastTurns = 5,
                ) as ContextStrategyConfig,
                ContextManagementType.Branching to ContextStrategyConfig.Branching as ContextStrategyConfig,
            ),
            repository = get(),
        )
    }

    // Domain services
    single<ChatService> { AiChatService(llmPort = get(), repository = get(), contextManager = get()) }
    single<SessionService> { AiSessionService(repository = get()) }
    single<BranchService> { AiBranchService(repository = get()) }
    single<UsageQueryService> { AiUsageQueryService(repository = get()) }

    // Domain Events
    single { SessionDeletedCleanupHandler(memoryService = get()) }

    single<DomainEventPublisher> {
        InProcessDomainEventPublisher(
            handlers = mapOf(
                DomainEvent.SessionDeleted::class to listOf(get<SessionDeletedCleanupHandler>()),
            ),
        )
    }

    // Application Services (use cases)
    single {
        SendMessageUseCase(
            chatService = get(),
            sessionService = get(),
            eventPublisher = get(),
        )
    }
    single {
        CreateSessionUseCase(
            sessionService = get(),
            eventPublisher = get(),
        )
    }
    single {
        DeleteSessionUseCase(
            sessionService = get(),
            eventPublisher = get(),
        )
    }
    single {
        ApplicationInitService(
            createSessionUseCase = get(),
            sessionService = get(),
        )
    }
}
```

- [ ] **Step 3: Verify full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add modules/presentation/app/
git commit -m "refactor(app): rewire DI to MemoryService, remove old fact/summary module dependencies"
```

---

### Task 7: Remove Old Data Modules

**Files:**
- Delete: `modules/data/fact-repository-exposed/` (entire directory)
- Delete: `modules/data/summary-repository-exposed/` (entire directory)
- Modify: `settings.gradle.kts` — remove old module includes

- [ ] **Step 1: Remove module includes from `settings.gradle.kts`**

Remove these lines:

```kotlin
include(":modules:data:summary-repository-exposed")
include(":modules:data:fact-repository-exposed")
```

- [ ] **Step 2: Delete old module directories**

```bash
rm -rf modules/data/fact-repository-exposed
rm -rf modules/data/summary-repository-exposed
```

- [ ] **Step 3: Verify full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: remove legacy fact-repository-exposed and summary-repository-exposed modules"
```

---

### Task 8: Add Debug Panel — Store and Component

**Files:**
- Create: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/debug/memory/MemoryDebugComponent.kt`
- Create: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/debug/memory/MemoryDebugStore.kt`
- Create: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/debug/memory/MemoryDebugStoreFactory.kt`

- [ ] **Step 1: Create `MemoryDebugStore.kt`**

```kotlin
package com.ai.challenge.ui.debug.memory

import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.mvikotlin.core.store.Store

interface MemoryDebugStore : Store<MemoryDebugStore.Intent, MemoryDebugStore.State, Nothing> {

    sealed interface Intent {
        data class LoadMemory(val sessionId: AgentSessionId) : Intent
        data class ReplaceFacts(val facts: List<Fact>) : Intent
        data class AddSummary(val summary: Summary) : Intent
        data class DeleteSummary(val summary: Summary) : Intent
    }

    data class State(
        val sessionId: AgentSessionId?,
        val facts: List<Fact>,
        val summaries: List<Summary>,
        val isLoading: Boolean,
        val error: String?,
    )
}
```

- [ ] **Step 2: Create `MemoryDebugStoreFactory.kt`**

```kotlin
package com.ai.challenge.ui.debug.memory

import com.ai.challenge.core.memory.usecase.AddSummaryUseCase
import com.ai.challenge.core.memory.usecase.DeleteSummaryUseCase
import com.ai.challenge.core.memory.usecase.GetMemoryUseCase
import com.ai.challenge.core.memory.usecase.UpdateFactsUseCase
import com.ai.challenge.core.session.AgentSessionId
import com.mvikotlin.core.store.Store
import com.mvikotlin.core.store.StoreFactory
import com.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class MemoryDebugStoreFactory(
    private val storeFactory: StoreFactory,
    private val getMemoryUseCase: GetMemoryUseCase,
    private val updateFactsUseCase: UpdateFactsUseCase,
    private val addSummaryUseCase: AddSummaryUseCase,
    private val deleteSummaryUseCase: DeleteSummaryUseCase,
) {

    fun create(): MemoryDebugStore =
        object : MemoryDebugStore,
            Store<MemoryDebugStore.Intent, MemoryDebugStore.State, Nothing> by storeFactory.create(
                name = "MemoryDebugStore",
                initialState = MemoryDebugStore.State(
                    sessionId = null,
                    facts = emptyList(),
                    summaries = emptyList(),
                    isLoading = false,
                    error = null,
                ),
                executorFactory = { Executor() },
                reducer = { msg: Msg ->
                    when (msg) {
                        is Msg.Loading -> copy(isLoading = true, error = null)
                        is Msg.Loaded -> copy(
                            sessionId = msg.sessionId,
                            facts = msg.facts,
                            summaries = msg.summaries,
                            isLoading = false,
                            error = null,
                        )
                        is Msg.Error -> copy(isLoading = false, error = msg.message)
                        is Msg.FactsUpdated -> copy(facts = msg.facts)
                        is Msg.SummaryAdded -> copy(summaries = summaries + msg.summary)
                        is Msg.SummaryDeleted -> copy(summaries = summaries.filter { it != msg.summary })
                    }
                },
            ) {}

    private sealed interface Msg {
        data object Loading : Msg
        data class Loaded(
            val sessionId: AgentSessionId,
            val facts: List<com.ai.challenge.core.fact.Fact>,
            val summaries: List<com.ai.challenge.core.summary.Summary>,
        ) : Msg
        data class Error(val message: String) : Msg
        data class FactsUpdated(val facts: List<com.ai.challenge.core.fact.Fact>) : Msg
        data class SummaryAdded(val summary: com.ai.challenge.core.summary.Summary) : Msg
        data class SummaryDeleted(val summary: com.ai.challenge.core.summary.Summary) : Msg
    }

    private inner class Executor : CoroutineExecutor<MemoryDebugStore.Intent, Nothing, MemoryDebugStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: MemoryDebugStore.Intent) {
            when (intent) {
                is MemoryDebugStore.Intent.LoadMemory -> loadMemory(sessionId = intent.sessionId)
                is MemoryDebugStore.Intent.ReplaceFacts -> replaceFacts(facts = intent.facts)
                is MemoryDebugStore.Intent.AddSummary -> addSummary(summary = intent.summary)
                is MemoryDebugStore.Intent.DeleteSummary -> deleteSummary(summary = intent.summary)
            }
        }

        private fun loadMemory(sessionId: AgentSessionId) {
            dispatch(message = Msg.Loading)
            scope.launch {
                val snapshot = getMemoryUseCase.execute(sessionId = sessionId)
                dispatch(message = Msg.Loaded(sessionId = sessionId, facts = snapshot.facts, summaries = snapshot.summaries))
            }
        }

        private fun replaceFacts(facts: List<com.ai.challenge.core.fact.Fact>) {
            val sessionId = state().sessionId ?: return
            scope.launch {
                updateFactsUseCase.execute(sessionId = sessionId, facts = facts).fold(
                    ifLeft = { dispatch(message = Msg.Error(message = it.message)) },
                    ifRight = { dispatch(message = Msg.FactsUpdated(facts = facts)) },
                )
            }
        }

        private fun addSummary(summary: com.ai.challenge.core.summary.Summary) {
            scope.launch {
                addSummaryUseCase.execute(summary = summary).fold(
                    ifLeft = { dispatch(message = Msg.Error(message = it.message)) },
                    ifRight = { dispatch(message = Msg.SummaryAdded(summary = summary)) },
                )
            }
        }

        private fun deleteSummary(summary: com.ai.challenge.core.summary.Summary) {
            scope.launch {
                deleteSummaryUseCase.execute(summary = summary).fold(
                    ifLeft = { dispatch(message = Msg.Error(message = it.message)) },
                    ifRight = { dispatch(message = Msg.SummaryDeleted(summary = summary)) },
                )
            }
        }
    }
}
```

- [ ] **Step 3: Create `MemoryDebugComponent.kt`**

```kotlin
package com.ai.challenge.ui.debug.memory

import com.ai.challenge.core.session.AgentSessionId
import com.arkivanov.decompose.ComponentContext
import com.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow

class MemoryDebugComponent(
    componentContext: ComponentContext,
    private val storeFactory: MemoryDebugStoreFactory,
) : ComponentContext by componentContext {

    private val store = storeFactory.create()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<MemoryDebugStore.State> = store.stateFlow

    fun onIntent(intent: MemoryDebugStore.Intent) {
        store.accept(intent = intent)
    }

    fun loadForSession(sessionId: AgentSessionId) {
        store.accept(intent = MemoryDebugStore.Intent.LoadMemory(sessionId = sessionId))
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :modules:presentation:compose-ui:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/debug/
git commit -m "feat(ui): add MemoryDebugStore and MemoryDebugComponent for memory debug panel"
```

---

### Task 9: Add Debug Panel — Compose Screen

**Files:**
- Create: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/debug/memory/MemoryDebugScreen.kt`

- [ ] **Step 1: Create `MemoryDebugScreen.kt`**

```kotlin
package com.ai.challenge.ui.debug.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.summary.Summary

@Composable
fun MemoryDebugScreen(component: MemoryDebugComponent) {
    val state by component.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(all = 16.dp),
    ) {
        Text(text = "Memory Debug", style = MaterialTheme.typography.headlineSmall)

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(alignment = Alignment.CenterHorizontally))
            return@Column
        }

        state.error?.let { error ->
            Text(text = error, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(height = 8.dp))
        }

        if (state.sessionId == null) {
            Text(text = "No session selected")
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(text = "Facts (${state.facts.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(height = 8.dp))
            }

            items(items = state.facts, key = { "${it.category}-${it.key.value}" }) { fact ->
                FactRow(fact = fact)
            }

            item {
                Spacer(modifier = Modifier.height(height = 16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(height = 16.dp))
                Text(text = "Summaries (${state.summaries.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(height = 8.dp))
            }

            items(items = state.summaries, key = { "${it.fromTurnIndex.value}-${it.toTurnIndex.value}-${it.createdAt.value}" }) { summary ->
                SummaryCard(
                    summary = summary,
                    onDelete = { component.onIntent(intent = MemoryDebugStore.Intent.DeleteSummary(summary = summary)) },
                )
                Spacer(modifier = Modifier.height(height = 8.dp))
            }
        }
    }
}

@Composable
private fun FactRow(fact: Fact) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = fact.category.name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(width = 80.dp),
        )
        Text(
            text = fact.key.value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(width = 120.dp),
        )
        Text(
            text = fact.value.value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(weight = 1f),
        )
    }
}

@Composable
private fun SummaryCard(summary: Summary, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(all = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Turns ${summary.fromTurnIndex.value}..${summary.toTurnIndex.value}",
                    style = MaterialTheme.typography.labelMedium,
                )
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete summary")
                }
            }
            Text(
                text = summary.content.value,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 5,
            )
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :modules:presentation:compose-ui:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/debug/memory/MemoryDebugScreen.kt
git commit -m "feat(ui): add MemoryDebugScreen composable for memory visualization"
```

---

### Task 10: Wire Debug Panel into Root Component and DI

**Files:**
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootComponent.kt` — add MemoryDebugComponent
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootContent.kt` — render debug panel
- Modify: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt` — register MemoryDebugStoreFactory

- [ ] **Step 1: Read current `RootComponent.kt` to understand the integration point**

Read the file to see how other components are wired.

- [ ] **Step 2: Add `MemoryDebugComponent` as a child in `RootComponent`**

Add `MemoryDebugComponent` creation alongside existing child components. Wire it to receive session ID changes from the active session.

- [ ] **Step 3: Read current `RootContent.kt` to understand the layout**

Read the file to see how the UI is structured.

- [ ] **Step 4: Add debug panel to `RootContent.kt`**

Add the debug panel as a collapsible side panel or tab alongside the main content area. Use `MemoryDebugScreen(component = rootComponent.memoryDebugComponent)`.

- [ ] **Step 5: Register `MemoryDebugStoreFactory` in `AppModule.kt`**

Add to `AppModule.kt`:

```kotlin
single {
    MemoryDebugStoreFactory(
        storeFactory = get(),
        getMemoryUseCase = get(),
        updateFactsUseCase = get(),
        addSummaryUseCase = get(),
        deleteSummaryUseCase = get(),
    )
}
```

- [ ] **Step 6: Verify full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Manual test — start the app**

Run: `OPENROUTER_API_KEY=<key> ./gradlew :modules:presentation:app:run`
Expected: App starts, debug panel visible, shows memory for current session.

- [ ] **Step 8: Commit**

```bash
git add modules/presentation/
git commit -m "feat(ui): wire MemoryDebugComponent into root layout with DI"
```

---

### Task 11: Update CLAUDE.md and Final Cleanup

**Files:**
- Modify: `CLAUDE.md` — update architecture description
- Delete old packages if any remain (verify no stale files)

- [ ] **Step 1: Update CLAUDE.md Architecture section**

Update Layer 0 to include memory package:
```
### Layer 0 — Foundation (`modules/core`)
- **core** — Domain models (...), memory ports (MemoryService, MemoryProvider, MemoryType, MemoryScope, MemorySnapshot, FactMemoryProvider, SummaryMemoryProvider), memory use cases (GetMemoryUseCase, UpdateFactsUseCase, AddSummaryUseCase, DeleteSummaryUseCase), ...
```

Update Layer 1 to replace old data modules:
```
### Layer 1 — Data (`modules/data/*`)
- **memory-repository-exposed** — FactRepository and SummaryRepository implementations (Exposed + SQLite, unified memory.db)
```

Add new module to Layer 2:
```
### Layer 2 — Domain (`modules/domain/*`)
- **memory-service** — MemoryService implementation (DefaultMemoryService, registry pattern), FactMemoryProvider/SummaryMemoryProvider implementations, SessionDeletedCleanupHandler (domain event handler), memory use case implementations
```

Update Layer 3 compose-ui description to mention debug panel.

- [ ] **Step 2: Verify full build and all tests**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests PASS

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md with Memory Layer architecture"
```

---

### Task 12: Final Verification

- [ ] **Step 1: Run full test suite**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 2: Start the application**

Run: `OPENROUTER_API_KEY=<key> ./gradlew :modules:presentation:app:run`
Expected: App starts normally. Verify:
- Chat works with all context management strategies
- Debug panel shows facts/summaries for active session
- Deleting a session cleans up memory (check debug panel for another session)

- [ ] **Step 3: Verify data migration**

If old `facts.db` or `sessions.db` (for summaries) exist in `~/.ai-challenge/`, verify:
- `memory.db` is created with data migrated
- Old database files are removed

- [ ] **Step 4: Final commit if any remaining changes**

```bash
git status
# If clean, no commit needed
```
