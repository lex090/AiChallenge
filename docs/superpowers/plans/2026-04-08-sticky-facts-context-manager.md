# Sticky Facts Context Manager — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `StickyFacts` context management strategy that extracts categorized key-value facts from conversation via LLM and sends facts + last 5 turns as context.

**Architecture:** New branch in existing `DefaultContextManager` (`StickyFacts` variant in `ContextManagementType`). New `FactExtractor` interface + `LlmFactExtractor` in context-manager module. New `Fact` model + `FactRepository` in core, `ExposedFactRepository` in a new data module.

**Tech Stack:** Kotlin, Exposed/SQLite, Ktor MockEngine (tests), kotlinx.serialization (JSON parsing), OpenRouterService DSL.

---

## File Map

### Core module (modify)
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementType.kt` — add `StickyFacts` variant

### Core module (create)
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactCategory.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactId.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/Fact.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactRepository.kt`

### Data module (create new module)
- Create: `modules/data/fact-repository-exposed/build.gradle.kts`
- Create: `modules/data/fact-repository-exposed/src/main/kotlin/com/ai/challenge/fact/repository/FactsTable.kt`
- Create: `modules/data/fact-repository-exposed/src/main/kotlin/com/ai/challenge/fact/repository/ExposedFactRepository.kt`
- Create: `modules/data/fact-repository-exposed/src/main/kotlin/com/ai/challenge/fact/repository/DatabaseFactory.kt`
- Create: `modules/data/fact-repository-exposed/src/test/kotlin/com/ai/challenge/fact/repository/ExposedFactRepositoryTest.kt`

### Domain module (modify + create)
- Create: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/FactExtractor.kt`
- Create: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/LlmFactExtractor.kt`
- Modify: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt`
- Modify: `modules/domain/context-manager/build.gradle.kts` — add serialization dependency
- Create: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/LlmFactExtractorTest.kt`
- Modify: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt`

### Data module (modify)
- Modify: `modules/data/context-management-repository-exposed/src/main/kotlin/com/ai/challenge/context/repository/ExposedContextManagementTypeRepository.kt` — add `sticky_facts` mapping

### Integration (modify)
- Modify: `settings.gradle.kts` — include new module
- Modify: `modules/presentation/app/build.gradle.kts` — add dependency
- Modify: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt` — wire new bindings

---

### Task 1: Core domain models — FactCategory, FactId, Fact

**Files:**
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactCategory.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactId.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/Fact.kt`

- [ ] **Step 1: Create FactCategory enum**

```kotlin
// modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactCategory.kt
package com.ai.challenge.core.fact

enum class FactCategory {
    Goal,
    Constraint,
    Preference,
    Decision,
    Agreement,
}
```

- [ ] **Step 2: Create FactId value class**

```kotlin
// modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactId.kt
package com.ai.challenge.core.fact

import java.util.UUID

@JvmInline
value class FactId(val value: String) {
    companion object {
        fun generate(): FactId = FactId(UUID.randomUUID().toString())
    }
}
```

- [ ] **Step 3: Create Fact data class**

```kotlin
// modules/core/src/main/kotlin/com/ai/challenge/core/fact/Fact.kt
package com.ai.challenge.core.fact

data class Fact(
    val id: FactId,
    val category: FactCategory,
    val key: String,
    val value: String,
)
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :modules:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/fact/
git commit -m "feat: add Fact domain model with FactCategory and FactId"
```

---

### Task 2: Core — FactRepository interface

**Files:**
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactRepository.kt`

- [ ] **Step 1: Create FactRepository interface**

```kotlin
// modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactRepository.kt
package com.ai.challenge.core.fact

import com.ai.challenge.core.session.AgentSessionId

interface FactRepository {
    suspend fun save(sessionId: AgentSessionId, facts: List<Fact>)
    suspend fun getBySession(sessionId: AgentSessionId): List<Fact>
    suspend fun deleteBySession(sessionId: AgentSessionId)
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :modules:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactRepository.kt
git commit -m "feat: add FactRepository interface"
```

---

### Task 3: Core — Add StickyFacts variant to ContextManagementType

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementType.kt`

- [ ] **Step 1: Add StickyFacts to sealed interface**

Replace the full file content with:

```kotlin
// modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementType.kt
package com.ai.challenge.core.context

sealed interface ContextManagementType {
    data object None : ContextManagementType
    data object SummarizeOnThreshold : ContextManagementType
    data object StickyFacts : ContextManagementType
}
```

This will cause compile errors in `ExposedContextManagementTypeRepository.kt` and `DefaultContextManager.kt` because the `when` expressions are no longer exhaustive. We'll fix those in subsequent tasks.

- [ ] **Step 2: Verify core compiles**

Run: `./gradlew :modules:core:compileKotlin`
Expected: BUILD SUCCESSFUL (core itself is fine; dependent modules will fail until updated)

- [ ] **Step 3: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementType.kt
git commit -m "feat: add StickyFacts variant to ContextManagementType"
```

---

### Task 4: Data — ExposedContextManagementTypeRepository mapping update

**Files:**
- Modify: `modules/data/context-management-repository-exposed/src/main/kotlin/com/ai/challenge/context/repository/ExposedContextManagementTypeRepository.kt`

- [ ] **Step 1: Add sticky_facts to both mapping functions**

In `ExposedContextManagementTypeRepository.kt`, replace the two private functions at the bottom:

Old:
```kotlin
private fun ContextManagementType.toStorageString(): String = when (this) {
    is ContextManagementType.None -> "none"
    is ContextManagementType.SummarizeOnThreshold -> "summarize_on_threshold"
}

private fun String.toContextManagementType(): ContextManagementType = when (this) {
    "none" -> ContextManagementType.None
    "summarize_on_threshold" -> ContextManagementType.SummarizeOnThreshold
    else -> ContextManagementType.None
}
```

New:
```kotlin
private fun ContextManagementType.toStorageString(): String = when (this) {
    is ContextManagementType.None -> "none"
    is ContextManagementType.SummarizeOnThreshold -> "summarize_on_threshold"
    is ContextManagementType.StickyFacts -> "sticky_facts"
}

private fun String.toContextManagementType(): ContextManagementType = when (this) {
    "none" -> ContextManagementType.None
    "summarize_on_threshold" -> ContextManagementType.SummarizeOnThreshold
    "sticky_facts" -> ContextManagementType.StickyFacts
    else -> ContextManagementType.None
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :modules:data:context-management-repository-exposed:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add modules/data/context-management-repository-exposed/src/main/kotlin/com/ai/challenge/context/repository/ExposedContextManagementTypeRepository.kt
git commit -m "feat: add sticky_facts mapping to ExposedContextManagementTypeRepository"
```

---

### Task 5: Data — fact-repository-exposed module scaffold

**Files:**
- Create: `modules/data/fact-repository-exposed/build.gradle.kts`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create build.gradle.kts**

```kotlin
// modules/data/fact-repository-exposed/build.gradle.kts
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

- [ ] **Step 2: Add module to settings.gradle.kts**

In `settings.gradle.kts`, add `include(":modules:data:fact-repository-exposed")` in the Layer 1: Data section, after the `context-management-repository-exposed` line:

Old:
```kotlin
include(":modules:data:context-management-repository-exposed")
```

New:
```kotlin
include(":modules:data:context-management-repository-exposed")
include(":modules:data:fact-repository-exposed")
```

- [ ] **Step 3: Verify Gradle sync**

Run: `./gradlew :modules:data:fact-repository-exposed:dependencies`
Expected: BUILD SUCCESSFUL (module is recognized)

- [ ] **Step 4: Commit**

```bash
git add modules/data/fact-repository-exposed/build.gradle.kts settings.gradle.kts
git commit -m "feat: scaffold fact-repository-exposed module"
```

---

### Task 6: Data — FactsTable and DatabaseFactory

**Files:**
- Create: `modules/data/fact-repository-exposed/src/main/kotlin/com/ai/challenge/fact/repository/FactsTable.kt`
- Create: `modules/data/fact-repository-exposed/src/main/kotlin/com/ai/challenge/fact/repository/DatabaseFactory.kt`

- [ ] **Step 1: Create FactsTable**

```kotlin
// modules/data/fact-repository-exposed/src/main/kotlin/com/ai/challenge/fact/repository/FactsTable.kt
package com.ai.challenge.fact.repository

import org.jetbrains.exposed.sql.Table

object FactsTable : Table("facts") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val category = varchar("category", 50)
    val key = varchar("key", 255)
    val value = text("value")

    override val primaryKey = PrimaryKey(id)
}
```

- [ ] **Step 2: Create DatabaseFactory**

```kotlin
// modules/data/fact-repository-exposed/src/main/kotlin/com/ai/challenge/fact/repository/DatabaseFactory.kt
package com.ai.challenge.fact.repository

import org.jetbrains.exposed.sql.Database
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

fun createFactDatabase(): Database {
    val dbDir = Path(System.getProperty("user.home"), ".ai-challenge")
    dbDir.createDirectories()
    val dbPath = dbDir.resolve("facts.db")
    return Database.connect(
        url = "jdbc:sqlite:$dbPath",
        driver = "org.sqlite.JDBC",
        setupConnection = { conn ->
            conn.createStatement().execute("PRAGMA foreign_keys = ON")
        },
    )
}
```

- [ ] **Step 3: Verify build**

Run: `./gradlew :modules:data:fact-repository-exposed:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add modules/data/fact-repository-exposed/src/
git commit -m "feat: add FactsTable and DatabaseFactory for fact-repository-exposed"
```

---

### Task 7: Data — ExposedFactRepository (TDD)

**Files:**
- Create: `modules/data/fact-repository-exposed/src/test/kotlin/com/ai/challenge/fact/repository/ExposedFactRepositoryTest.kt`
- Create: `modules/data/fact-repository-exposed/src/main/kotlin/com/ai/challenge/fact/repository/ExposedFactRepository.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
// modules/data/fact-repository-exposed/src/test/kotlin/com/ai/challenge/fact/repository/ExposedFactRepositoryTest.kt
package com.ai.challenge.fact.repository

import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactId
import com.ai.challenge.core.session.AgentSessionId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExposedFactRepositoryTest {

    private lateinit var repo: ExposedFactRepository

    @BeforeTest
    fun setup() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_fact_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        repo = ExposedFactRepository(database = db)
    }

    @Test
    fun `save and retrieve facts by session`() = runTest {
        val sessionId = AgentSessionId("s1")
        val facts = listOf(
            Fact(id = FactId.generate(), category = FactCategory.Goal, key = "main_goal", value = "Build a chat bot"),
            Fact(id = FactId.generate(), category = FactCategory.Constraint, key = "language", value = "Kotlin only"),
        )

        repo.save(sessionId = sessionId, facts = facts)
        val result = repo.getBySession(sessionId = sessionId)

        assertEquals(2, result.size)
        assertEquals("main_goal", result.first { it.category == FactCategory.Goal }.key)
        assertEquals("Build a chat bot", result.first { it.category == FactCategory.Goal }.value)
        assertEquals("language", result.first { it.category == FactCategory.Constraint }.key)
        assertEquals("Kotlin only", result.first { it.category == FactCategory.Constraint }.value)
    }

    @Test
    fun `save overwrites all previous facts`() = runTest {
        val sessionId = AgentSessionId("s1")
        val firstFacts = listOf(
            Fact(id = FactId.generate(), category = FactCategory.Goal, key = "goal", value = "Old goal"),
        )
        repo.save(sessionId = sessionId, facts = firstFacts)

        val secondFacts = listOf(
            Fact(id = FactId.generate(), category = FactCategory.Goal, key = "goal", value = "New goal"),
            Fact(id = FactId.generate(), category = FactCategory.Decision, key = "framework", value = "Ktor"),
        )
        repo.save(sessionId = sessionId, facts = secondFacts)

        val result = repo.getBySession(sessionId = sessionId)
        assertEquals(2, result.size)
        assertEquals("New goal", result.first { it.category == FactCategory.Goal }.value)
    }

    @Test
    fun `deleteBySession removes all facts`() = runTest {
        val sessionId = AgentSessionId("s1")
        val facts = listOf(
            Fact(id = FactId.generate(), category = FactCategory.Goal, key = "goal", value = "A goal"),
        )
        repo.save(sessionId = sessionId, facts = facts)

        repo.deleteBySession(sessionId = sessionId)
        val result = repo.getBySession(sessionId = sessionId)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getBySession returns empty list for unknown session`() = runTest {
        val result = repo.getBySession(sessionId = AgentSessionId("unknown"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `facts from different sessions are isolated`() = runTest {
        val s1 = AgentSessionId("s1")
        val s2 = AgentSessionId("s2")

        repo.save(sessionId = s1, facts = listOf(
            Fact(id = FactId.generate(), category = FactCategory.Goal, key = "goal", value = "S1 goal"),
        ))
        repo.save(sessionId = s2, facts = listOf(
            Fact(id = FactId.generate(), category = FactCategory.Goal, key = "goal", value = "S2 goal"),
        ))

        val s1Result = repo.getBySession(sessionId = s1)
        assertEquals(1, s1Result.size)
        assertEquals("S1 goal", s1Result[0].value)

        val s2Result = repo.getBySession(sessionId = s2)
        assertEquals(1, s2Result.size)
        assertEquals("S2 goal", s2Result[0].value)
    }

    @Test
    fun `save with empty list clears all facts`() = runTest {
        val sessionId = AgentSessionId("s1")
        repo.save(sessionId = sessionId, facts = listOf(
            Fact(id = FactId.generate(), category = FactCategory.Goal, key = "goal", value = "A goal"),
        ))

        repo.save(sessionId = sessionId, facts = emptyList())
        val result = repo.getBySession(sessionId = sessionId)

        assertTrue(result.isEmpty())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :modules:data:fact-repository-exposed:test`
Expected: FAIL — `ExposedFactRepository` class does not exist yet

- [ ] **Step 3: Implement ExposedFactRepository**

```kotlin
// modules/data/fact-repository-exposed/src/main/kotlin/com/ai/challenge/fact/repository/ExposedFactRepository.kt
package com.ai.challenge.fact.repository

import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactId
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.session.AgentSessionId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedFactRepository(private val database: Database) : FactRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(FactsTable)
        }
    }

    override suspend fun save(sessionId: AgentSessionId, facts: List<Fact>) {
        transaction(database) {
            FactsTable.deleteWhere { FactsTable.sessionId eq sessionId.value }
            FactsTable.batchInsert(facts) { fact ->
                this[FactsTable.id] = fact.id.value
                this[FactsTable.sessionId] = sessionId.value
                this[FactsTable.category] = fact.category.toStorageString()
                this[FactsTable.key] = fact.key
                this[FactsTable.value] = fact.value
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
        id = FactId(this[FactsTable.id]),
        category = this[FactsTable.category].toFactCategory(),
        key = this[FactsTable.key],
        value = this[FactsTable.value],
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

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :modules:data:fact-repository-exposed:test`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add modules/data/fact-repository-exposed/src/
git commit -m "feat: implement ExposedFactRepository with tests"
```

---

### Task 8: Domain — FactExtractor interface

**Files:**
- Create: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/FactExtractor.kt`

- [ ] **Step 1: Create FactExtractor interface**

```kotlin
// modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/FactExtractor.kt
package com.ai.challenge.context

import com.ai.challenge.core.fact.Fact

interface FactExtractor {
    suspend fun extract(
        currentFacts: List<Fact>,
        newUserMessage: String,
        lastAssistantResponse: String?,
    ): List<Fact>
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :modules:domain:context-manager:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/FactExtractor.kt
git commit -m "feat: add FactExtractor interface"
```

---

### Task 9: Domain — LlmFactExtractor (TDD)

**Files:**
- Modify: `modules/domain/context-manager/build.gradle.kts` — add serialization plugin + dependency
- Create: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/LlmFactExtractorTest.kt`
- Create: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/LlmFactExtractor.kt`

- [ ] **Step 1: Add kotlinx-serialization to context-manager build.gradle.kts**

The `LlmFactExtractor` needs to parse JSON from the LLM. Add the serialization plugin and runtime dependency.

Old `plugins` block:
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}
```

New `plugins` block:
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
```

Add to `dependencies` block (alongside existing dependencies):
```kotlin
implementation(libs.ktor.serialization.kotlinx.json)
```

The full resulting dependencies block will be:
```kotlin
dependencies {
    implementation(project(":modules:core"))
    implementation(project(":modules:data:open-router-service"))
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.content.negotiation)
}
```

- [ ] **Step 2: Write the failing tests**

```kotlin
// modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/LlmFactExtractorTest.kt
package com.ai.challenge.context

import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactId
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

class LlmFactExtractorTest {

    private fun createExtractor(responseJson: String): Pair<LlmFactExtractor, () -> String?> {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"choices":[{"index":0,"message":{"role":"assistant","content":${Json.encodeToString(responseJson)}}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = false }) }
        }
        val service = OpenRouterService(apiKey = "test-key", client = client)
        val extractor = LlmFactExtractor(service = service, model = "test-model")
        return extractor to { capturedBody }
    }

    @Test
    fun `extract parses valid JSON response into facts`() = runTest {
        val responseJson = """[{"category":"Goal","key":"main_goal","value":"Build a chat bot"},{"category":"Constraint","key":"lang","value":"Kotlin only"}]"""
        val (extractor, _) = createExtractor(responseJson = responseJson)

        val result = extractor.extract(
            currentFacts = emptyList(),
            newUserMessage = "I want to build a Kotlin chat bot",
            lastAssistantResponse = null,
        )

        assertEquals(2, result.size)
        assertEquals(FactCategory.Goal, result[0].category)
        assertEquals("main_goal", result[0].key)
        assertEquals("Build a chat bot", result[0].value)
        assertEquals(FactCategory.Constraint, result[1].category)
        assertEquals("lang", result[1].key)
        assertEquals("Kotlin only", result[1].value)
    }

    @Test
    fun `extract sends correct prompt structure with no current facts`() = runTest {
        val responseJson = """[{"category":"Goal","key":"goal","value":"test"}]"""
        val (extractor, getCapturedBody) = createExtractor(responseJson = responseJson)

        extractor.extract(
            currentFacts = emptyList(),
            newUserMessage = "Hello",
            lastAssistantResponse = null,
        )

        val json = Json.parseToJsonElement(getCapturedBody()!!).jsonObject
        val messages = json["messages"]!!.jsonArray

        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertTrue(messages[0].jsonObject["content"]!!.jsonPrimitive.content.contains("Goal"))
        assertTrue(messages[0].jsonObject["content"]!!.jsonPrimitive.content.contains("Constraint"))

        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Hello", messages[1].jsonObject["content"]!!.jsonPrimitive.content)

        assertEquals("user", messages[2].jsonObject["role"]!!.jsonPrimitive.content)
        assertTrue(messages[2].jsonObject["content"]!!.jsonPrimitive.content.contains("Extract"))
    }

    @Test
    fun `extract sends current facts and last assistant response when present`() = runTest {
        val responseJson = """[{"category":"Goal","key":"goal","value":"Updated goal"}]"""
        val (extractor, getCapturedBody) = createExtractor(responseJson = responseJson)

        val currentFacts = listOf(
            Fact(id = FactId.generate(), category = FactCategory.Goal, key = "goal", value = "Old goal"),
        )

        extractor.extract(
            currentFacts = currentFacts,
            newUserMessage = "Actually, change the goal",
            lastAssistantResponse = "Sure, what would you like?",
        )

        val json = Json.parseToJsonElement(getCapturedBody()!!).jsonObject
        val messages = json["messages"]!!.jsonArray

        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)

        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertTrue(messages[1].jsonObject["content"]!!.jsonPrimitive.content.contains("Old goal"))

        assertEquals("assistant", messages[2].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Sure, what would you like?", messages[2].jsonObject["content"]!!.jsonPrimitive.content)

        assertEquals("user", messages[3].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Actually, change the goal", messages[3].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `extract returns empty list when LLM returns empty array`() = runTest {
        val (extractor, _) = createExtractor(responseJson = "[]")

        val result = extractor.extract(
            currentFacts = emptyList(),
            newUserMessage = "Hi",
            lastAssistantResponse = null,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `extract returns current facts on invalid JSON`() = runTest {
        val (extractor, _) = createExtractor(responseJson = "this is not valid json")

        val currentFacts = listOf(
            Fact(id = FactId.generate(), category = FactCategory.Goal, key = "goal", value = "Keep this"),
        )

        val result = extractor.extract(
            currentFacts = currentFacts,
            newUserMessage = "Something",
            lastAssistantResponse = null,
        )

        assertEquals(1, result.size)
        assertEquals("Keep this", result[0].value)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :modules:domain:context-manager:test --tests "com.ai.challenge.context.LlmFactExtractorTest"`
Expected: FAIL — `LlmFactExtractor` class does not exist yet

- [ ] **Step 4: Implement LlmFactExtractor**

```kotlin
// modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/LlmFactExtractor.kt
package com.ai.challenge.context

import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactId
import com.ai.challenge.llm.OpenRouterService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LlmFactExtractor(
    private val service: OpenRouterService,
    private val model: String,
) : FactExtractor {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun extract(
        currentFacts: List<Fact>,
        newUserMessage: String,
        lastAssistantResponse: String?,
    ): List<Fact> {
        val responseText = service.chatText(model) {
            jsonMode = true
            system(SYSTEM_PROMPT)
            if (currentFacts.isNotEmpty()) {
                user("Current facts:\n${formatFactsAsJson(facts = currentFacts)}")
            }
            if (lastAssistantResponse != null) {
                assistant(lastAssistantResponse)
            }
            user(newUserMessage)
            user("Extract and return the updated facts as a JSON array.")
        }
        return parseFacts(responseText = responseText, fallback = currentFacts)
    }

    private fun formatFactsAsJson(facts: List<Fact>): String {
        val entries = facts.joinToString(",\n  ") { fact ->
            """{"category":"${fact.category.name}","key":"${fact.key}","value":"${fact.value}"}"""
        }
        return "[\n  $entries\n]"
    }

    private fun parseFacts(responseText: String, fallback: List<Fact>): List<Fact> =
        try {
            json.parseToJsonElement(responseText).jsonArray.map { element ->
                val obj = element.jsonObject
                Fact(
                    id = FactId.generate(),
                    category = parseCategory(category = obj["category"]!!.jsonPrimitive.content),
                    key = obj["key"]!!.jsonPrimitive.content,
                    value = obj["value"]!!.jsonPrimitive.content,
                )
            }
        } catch (_: Exception) {
            fallback
        }

    private fun parseCategory(category: String): FactCategory = when (category) {
        "Goal" -> FactCategory.Goal
        "Constraint" -> FactCategory.Constraint
        "Preference" -> FactCategory.Preference
        "Decision" -> FactCategory.Decision
        "Agreement" -> FactCategory.Agreement
        else -> FactCategory.Goal
    }

    companion object {
        private val SYSTEM_PROMPT = """
            You extract and maintain structured facts from a conversation.
            
            Categories:
            - Goal: the user's objectives
            - Constraint: limitations and requirements
            - Preference: user preferences
            - Decision: decisions that have been made
            - Agreement: agreements between user and assistant
            
            Return a JSON array of objects with fields: "category", "key", "value".
            Each fact has a short descriptive key and a concise value.
            If previous facts are provided, update them: add new facts, modify changed ones, remove obsolete ones.
            Return ONLY the JSON array, no other text.
        """.trimIndent()
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :modules:domain:context-manager:test --tests "com.ai.challenge.context.LlmFactExtractorTest"`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add modules/domain/context-manager/build.gradle.kts
git add modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/LlmFactExtractor.kt
git add modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/LlmFactExtractorTest.kt
git commit -m "feat: implement LlmFactExtractor with tests"
```

---

### Task 10: Domain — DefaultContextManager StickyFacts branch (TDD)

**Files:**
- Modify: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt`
- Modify: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt`

- [ ] **Step 1: Add fake implementations to test file**

At the bottom of `DefaultContextManagerTest.kt`, add these two new fake classes (before the closing of the file, after the existing `InMemoryContextManagementTypeRepository`):

```kotlin
private class FakeFactExtractor : FactExtractor {
    var callCount = 0
        private set
    var lastCurrentFacts: List<Fact> = emptyList()
        private set
    var lastNewUserMessage: String = ""
        private set
    var lastAssistantResponse: String? = null
        private set
    var factsToReturn: List<Fact> = emptyList()

    override suspend fun extract(
        currentFacts: List<Fact>,
        newUserMessage: String,
        lastAssistantResponse: String?,
    ): List<Fact> {
        callCount++
        lastCurrentFacts = currentFacts
        lastNewUserMessage = newUserMessage
        this.lastAssistantResponse = lastAssistantResponse
        return factsToReturn
    }
}

private class InMemoryFactRepository : FactRepository {
    private val store = mutableMapOf<AgentSessionId, List<Fact>>()

    override suspend fun save(sessionId: AgentSessionId, facts: List<Fact>) {
        store[sessionId] = facts
    }

    override suspend fun getBySession(sessionId: AgentSessionId): List<Fact> =
        store[sessionId] ?: emptyList()

    override suspend fun deleteBySession(sessionId: AgentSessionId) {
        store.remove(sessionId)
    }
}
```

Also add these imports at the top:
```kotlin
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactId
import com.ai.challenge.core.fact.FactRepository
```

- [ ] **Step 2: Update setup and createManager in test file**

Add new fields to the test class:
```kotlin
private lateinit var fakeFactExtractor: FakeFactExtractor
private lateinit var fakeFactRepo: InMemoryFactRepository
```

In `setup()`, add:
```kotlin
fakeFactExtractor = FakeFactExtractor()
fakeFactRepo = InMemoryFactRepository()
```

Update `createManager()`:
```kotlin
private fun createManager(): DefaultContextManager =
    DefaultContextManager(
        contextManagementRepository = fakeContextManagementRepo,
        compressor = fakeCompressor,
        summaryRepository = fakeSummaryRepo,
        turnRepository = fakeTurnRepo,
        factExtractor = fakeFactExtractor,
        factRepository = fakeFactRepo,
    )
```

- [ ] **Step 3: Add StickyFacts test cases**

Add these test methods to the test class:

```kotlin
@Test
fun `stickyFacts extracts facts and includes system message`() = runTest {
    val sessionId = AgentSessionId("s1")
    fakeContextManagementRepo.save(sessionId, ContextManagementType.StickyFacts)
    saveTurns(sessionId, turns(3))
    fakeFactExtractor.factsToReturn = listOf(
        Fact(id = FactId.generate(), category = FactCategory.Goal, key = "goal", value = "Build a bot"),
    )
    val manager = createManager()

    val result = manager.prepareContext(sessionId, "new msg")

    assertTrue(result.compressed)
    assertEquals(3, result.originalTurnCount)
    assertEquals(3, result.retainedTurnCount)
    assertEquals(0, result.summaryCount)
    assertEquals(MessageRole.System, result.messages.first().role)
    assertTrue(result.messages.first().content.contains("Build a bot"))
    assertEquals(1, fakeFactExtractor.callCount)
}

@Test
fun `stickyFacts retains only last 5 turns`() = runTest {
    val sessionId = AgentSessionId("s1")
    fakeContextManagementRepo.save(sessionId, ContextManagementType.StickyFacts)
    saveTurns(sessionId, turns(8))
    fakeFactExtractor.factsToReturn = listOf(
        Fact(id = FactId.generate(), category = FactCategory.Goal, key = "goal", value = "A goal"),
    )
    val manager = createManager()

    val result = manager.prepareContext(sessionId, "new msg")

    assertTrue(result.compressed)
    assertEquals(8, result.originalTurnCount)
    assertEquals(5, result.retainedTurnCount)
    // system + 5 turns * 2 messages + new user message = 12
    assertEquals(12, result.messages.size)
    assertEquals(ContextMessage(MessageRole.User, "msg4"), result.messages[1])
}

@Test
fun `stickyFacts with no facts extracted omits system message`() = runTest {
    val sessionId = AgentSessionId("s1")
    fakeContextManagementRepo.save(sessionId, ContextManagementType.StickyFacts)
    saveTurns(sessionId, turns(2))
    fakeFactExtractor.factsToReturn = emptyList()
    val manager = createManager()

    val result = manager.prepareContext(sessionId, "new msg")

    assertFalse(result.compressed)
    assertEquals(2, result.originalTurnCount)
    assertEquals(2, result.retainedTurnCount)
    // 2 turns * 2 messages + new user message = 5 (no system)
    assertEquals(5, result.messages.size)
    assertEquals(MessageRole.User, result.messages.first().role)
}

@Test
fun `stickyFacts persists extracted facts`() = runTest {
    val sessionId = AgentSessionId("s1")
    fakeContextManagementRepo.save(sessionId, ContextManagementType.StickyFacts)
    val expectedFacts = listOf(
        Fact(id = FactId.generate(), category = FactCategory.Decision, key = "db", value = "SQLite"),
    )
    fakeFactExtractor.factsToReturn = expectedFacts
    val manager = createManager()

    manager.prepareContext(sessionId, "Use SQLite")

    val savedFacts = fakeFactRepo.getBySession(sessionId)
    assertEquals(1, savedFacts.size)
    assertEquals("SQLite", savedFacts[0].value)
}

@Test
fun `stickyFacts with empty history returns system message and new message`() = runTest {
    val sessionId = AgentSessionId("s1")
    fakeContextManagementRepo.save(sessionId, ContextManagementType.StickyFacts)
    fakeFactExtractor.factsToReturn = listOf(
        Fact(id = FactId.generate(), category = FactCategory.Goal, key = "goal", value = "Start fresh"),
    )
    val manager = createManager()

    val result = manager.prepareContext(sessionId, "hello")

    assertTrue(result.compressed)
    assertEquals(0, result.originalTurnCount)
    assertEquals(0, result.retainedTurnCount)
    assertEquals(2, result.messages.size)
    assertEquals(MessageRole.System, result.messages[0].role)
    assertEquals(ContextMessage(MessageRole.User, "hello"), result.messages[1])
}

@Test
fun `stickyFacts formats categories in system message`() = runTest {
    val sessionId = AgentSessionId("s1")
    fakeContextManagementRepo.save(sessionId, ContextManagementType.StickyFacts)
    fakeFactExtractor.factsToReturn = listOf(
        Fact(id = FactId.generate(), category = FactCategory.Goal, key = "goal", value = "Build bot"),
        Fact(id = FactId.generate(), category = FactCategory.Constraint, key = "lang", value = "Kotlin"),
        Fact(id = FactId.generate(), category = FactCategory.Preference, key = "style", value = "FP"),
        Fact(id = FactId.generate(), category = FactCategory.Decision, key = "db", value = "SQLite"),
        Fact(id = FactId.generate(), category = FactCategory.Agreement, key = "deadline", value = "Friday"),
    )
    val manager = createManager()

    val result = manager.prepareContext(sessionId, "msg")

    val systemContent = result.messages.first().content
    assertTrue(systemContent.contains("## Goals"))
    assertTrue(systemContent.contains("- goal: Build bot"))
    assertTrue(systemContent.contains("## Constraints"))
    assertTrue(systemContent.contains("- lang: Kotlin"))
    assertTrue(systemContent.contains("## Preferences"))
    assertTrue(systemContent.contains("- style: FP"))
    assertTrue(systemContent.contains("## Decisions"))
    assertTrue(systemContent.contains("- db: SQLite"))
    assertTrue(systemContent.contains("## Agreements"))
    assertTrue(systemContent.contains("- deadline: Friday"))
}

@Test
fun `stickyFacts passes last assistant response to extractor`() = runTest {
    val sessionId = AgentSessionId("s1")
    fakeContextManagementRepo.save(sessionId, ContextManagementType.StickyFacts)
    saveTurns(sessionId, listOf(Turn(userMessage = "hi", agentResponse = "hello there")))
    fakeFactExtractor.factsToReturn = emptyList()
    val manager = createManager()

    manager.prepareContext(sessionId, "next msg")

    assertEquals("hello there", fakeFactExtractor.lastAssistantResponse)
    assertEquals("next msg", fakeFactExtractor.lastNewUserMessage)
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew :modules:domain:context-manager:test`
Expected: FAIL — `DefaultContextManager` constructor doesn't accept `factExtractor`/`factRepository` yet, and the `when` on `ContextManagementType` is not exhaustive

- [ ] **Step 5: Update DefaultContextManager implementation**

Replace the full `DefaultContextManager.kt`:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextManagementTypeRepository
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.ContextManager.PreparedContext
import com.ai.challenge.core.context.ContextManager.PreparedContext.ContextMessage
import com.ai.challenge.core.context.ContextManager.PreparedContext.ContextMessage.MessageRole
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnRepository

class DefaultContextManager(
    private val contextManagementRepository: ContextManagementTypeRepository,
    private val compressor: ContextCompressor,
    private val summaryRepository: SummaryRepository,
    private val turnRepository: TurnRepository,
    private val factExtractor: FactExtractor,
    private val factRepository: FactRepository,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        newMessage: String,
    ): PreparedContext {
        val type = contextManagementRepository.getBySession(sessionId = sessionId)

        return when (type) {
            is ContextManagementType.None -> passThrough(sessionId = sessionId, newMessage = newMessage)
            is ContextManagementType.SummarizeOnThreshold -> summarizeOnThreshold(
                sessionId = sessionId,
                newMessage = newMessage
            )
            is ContextManagementType.StickyFacts -> stickyFacts(sessionId = sessionId, newMessage = newMessage)
        }
    }

    // --- orchestration (side effects at boundaries) ---

    private suspend fun passThrough(
        sessionId: AgentSessionId,
        newMessage: String,
    ): PreparedContext {
        val history = turnRepository.getBySession(sessionId = sessionId)
        return withoutCompression(history = history, newMessage = newMessage)
    }

    private suspend fun summarizeOnThreshold(
        sessionId: AgentSessionId,
        newMessage: String,
    ): PreparedContext {
        val maxTurns = 15
        val retainLast = 5
        val compressionInterval = 10

        val history = turnRepository.getBySession(sessionId = sessionId)

        if (history.size < maxTurns) {
            return withoutCompression(history = history, newMessage = newMessage)
        }

        val lastSummary = summaryRepository.getBySession(sessionId = sessionId).maxByOrNull { it.toTurnIndex }

        if (lastSummary != null) {
            val turnsSinceLastSummary = history.size - lastSummary.toTurnIndex
            if (turnsSinceLastSummary < retainLast + compressionInterval) {
                return withExistingSummary(summary = lastSummary, history = history, newMessage = newMessage)
            }
        }

        val splitAt = (history.size - retainLast).coerceAtLeast(minimumValue = 0)
        val summaryText = compressTurns(history = history, splitAt = splitAt, lastSummary = lastSummary)
        saveSummary(sessionId = sessionId, summaryText = summaryText, toTurnIndex = splitAt)
        return withNewSummary(summaryText = summaryText, history = history, splitAt = splitAt, newMessage = newMessage)
    }

    private suspend fun stickyFacts(
        sessionId: AgentSessionId,
        newMessage: String,
    ): PreparedContext {
        val retainLast = 5

        val currentFacts = factRepository.getBySession(sessionId = sessionId)
        val history = turnRepository.getBySession(sessionId = sessionId)
        val lastAssistantResponse = history.lastOrNull()?.agentResponse

        val updatedFacts = factExtractor.extract(
            currentFacts = currentFacts,
            newUserMessage = newMessage,
            lastAssistantResponse = lastAssistantResponse,
        )
        factRepository.save(sessionId = sessionId, facts = updatedFacts)

        val retained = if (history.size > retainLast) {
            history.subList(history.size - retainLast, history.size)
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

    // --- side effects ---

    private suspend fun compressTurns(
        history: List<Turn>,
        splitAt: Int,
        lastSummary: Summary?,
    ): String = when (lastSummary) {
        null -> compressor.compress(turns = history.subList(0, splitAt))
        else -> compressor.compress(
            turns = history.subList(lastSummary.toTurnIndex, splitAt),
            previousSummary = lastSummary,
        )
    }

    private suspend fun saveSummary(
        sessionId: AgentSessionId,
        summaryText: String,
        toTurnIndex: Int,
    ) {
        summaryRepository.save(
            sessionId = sessionId,
            summary = Summary(text = summaryText, fromTurnIndex = 0, toTurnIndex = toTurnIndex),
        )
    }

    // --- pure functions ---

    private fun turnsToMessages(turns: List<Turn>): List<ContextMessage> =
        turns.flatMap {
            listOf(
                ContextMessage(role = MessageRole.User, content = it.userMessage),
                ContextMessage(role = MessageRole.Assistant, content = it.agentResponse),
            )
        }

    private fun withoutCompression(history: List<Turn>, newMessage: String): PreparedContext =
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
        newMessage: String,
    ): PreparedContext {
        val retained = history.subList(summary.toTurnIndex, history.size)
        return PreparedContext(
            messages = summarizedMessages(
                summaryText = summary.text,
                retainedTurns = retained,
                newMessage = newMessage
            ),
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = retained.size,
            summaryCount = 1,
        )
    }

    private fun withNewSummary(
        summaryText: String,
        history: List<Turn>,
        splitAt: Int,
        newMessage: String,
    ): PreparedContext {
        val retained = history.subList(splitAt, history.size)
        return PreparedContext(
            messages = summarizedMessages(summaryText = summaryText, retainedTurns = retained, newMessage = newMessage),
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = retained.size,
            summaryCount = 1,
        )
    }

    private fun withFacts(
        facts: List<Fact>,
        retainedTurns: List<Turn>,
        history: List<Turn>,
        newMessage: String,
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
        newMessage: String,
    ): List<ContextMessage> =
        buildList {
            add(ContextMessage(role = MessageRole.System, content = formatFacts(facts = facts)))
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
            appendLine("- ${fact.key}: ${fact.value}")
        }
        appendLine()
    }

    private fun summarizedMessages(
        summaryText: String,
        retainedTurns: List<Turn>,
        newMessage: String,
    ): List<ContextMessage> =
        buildList {
            add(ContextMessage(role = MessageRole.System, content = "Previous conversation summary:\n$summaryText"))
            addAll(turnsToMessages(turns = retainedTurns))
            add(ContextMessage(role = MessageRole.User, content = newMessage))
        }
}
```

- [ ] **Step 6: Run all context-manager tests**

Run: `./gradlew :modules:domain:context-manager:test`
Expected: ALL PASS

- [ ] **Step 7: Commit**

```bash
git add modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt
git add modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt
git commit -m "feat: add StickyFacts branch to DefaultContextManager"
```

---

### Task 11: Integration — DI wiring

**Files:**
- Modify: `modules/presentation/app/build.gradle.kts`
- Modify: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt`

- [ ] **Step 1: Add fact-repository-exposed dependency to app build.gradle.kts**

In `modules/presentation/app/build.gradle.kts`, add to the dependencies block:

```kotlin
implementation(project(":modules:data:fact-repository-exposed"))
```

Add it after the `context-management-repository-exposed` line.

- [ ] **Step 2: Update AppModule.kt with new bindings**

Add imports at the top of `AppModule.kt`:
```kotlin
import com.ai.challenge.context.FactExtractor
import com.ai.challenge.context.LlmFactExtractor
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.fact.repository.ExposedFactRepository
import com.ai.challenge.fact.repository.createFactDatabase
```

Add new Koin bindings (after the `ContextCompressor` binding, before the `ContextManager` binding):
```kotlin
single<FactRepository> { ExposedFactRepository(database = createFactDatabase()) }
single<FactExtractor> { LlmFactExtractor(service = get(), model = "google/gemini-2.0-flash-001") }
```

Update the `DefaultContextManager` binding to pass new dependencies:
```kotlin
single<ContextManager> {
    DefaultContextManager(
        contextManagementRepository = get(),
        compressor = get(),
        summaryRepository = get(),
        turnRepository = get(),
        factExtractor = get(),
        factRepository = get(),
    )
}
```

- [ ] **Step 3: Verify full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add modules/presentation/app/build.gradle.kts
git add modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt
git commit -m "feat: wire StickyFacts dependencies in AppModule"
```

---

### Task 12: Final verification — all tests pass

- [ ] **Step 1: Run full test suite**

Run: `./gradlew test`
Expected: ALL PASS

- [ ] **Step 2: Run full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Final commit if any fixups needed**

Only if changes were needed to fix issues discovered in this step.
