# Branching Context Management — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a third context management strategy "Branching" that allows creating dialog branches from any Turn, switching between branches, and cascading deletion — with three competing UI variants built in separate worktrees.

**Architecture:** Backend logic (Tasks 1–9) lands in `wt-4`. Three UI variants (Tasks 10A/10B/10C) each get their own worktree forked from `wt-4` after Task 9. Each layer (core → data → domain → presentation) is built bottom-up with TDD.

**Tech Stack:** Kotlin 2.3.20, Exposed 0.61.0/SQLite, Arrow 2.1.2, MVIKotlin 4.3.0, Decompose 3.5.0, Compose Multiplatform 1.10.3, Koin 4.1.0

**Project rules (from CLAUDE.md):** All arguments named at call sites. No default parameter values in declarations. Dependencies via version catalog (`libs.*`). Repository naming: `{DomainModel}Repository`. Arrow Either at domain boundaries.

---

## File Map

### New files (core)
- `modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchId.kt`
- `modules/core/src/main/kotlin/com/ai/challenge/core/branch/Branch.kt`
- `modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchRepository.kt`
- `modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchTurnRepository.kt`

### New files (data — branch-repository-exposed)
- `modules/data/branch-repository-exposed/build.gradle.kts`
- `modules/data/branch-repository-exposed/src/main/kotlin/com/ai/challenge/branch/repository/BranchesTable.kt`
- `modules/data/branch-repository-exposed/src/main/kotlin/com/ai/challenge/branch/repository/ExposedBranchRepository.kt`
- `modules/data/branch-repository-exposed/src/main/kotlin/com/ai/challenge/branch/repository/DatabaseFactory.kt`
- `modules/data/branch-repository-exposed/src/test/kotlin/com/ai/challenge/branch/repository/ExposedBranchRepositoryTest.kt`

### New files (data — branch-turn-repository-exposed)
- `modules/data/branch-turn-repository-exposed/build.gradle.kts`
- `modules/data/branch-turn-repository-exposed/src/main/kotlin/com/ai/challenge/branch/turn/repository/BranchTurnsTable.kt`
- `modules/data/branch-turn-repository-exposed/src/main/kotlin/com/ai/challenge/branch/turn/repository/ExposedBranchTurnRepository.kt`
- `modules/data/branch-turn-repository-exposed/src/main/kotlin/com/ai/challenge/branch/turn/repository/DatabaseFactory.kt`
- `modules/data/branch-turn-repository-exposed/src/test/kotlin/com/ai/challenge/branch/turn/repository/ExposedBranchTurnRepositoryTest.kt`

### New files (domain — context-manager)
- `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/BranchingContextManager.kt`
- `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/BranchingContextManagerTest.kt`

### Modified files
- `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementType.kt` — add `Branching`
- `modules/core/src/main/kotlin/com/ai/challenge/core/agent/Agent.kt` — add branch methods
- `modules/data/context-management-repository-exposed/src/main/kotlin/com/ai/challenge/context/repository/ExposedContextManagementTypeRepository.kt` — handle "branching" serialization
- `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt` — delegate to BranchingContextManager
- `modules/domain/context-manager/build.gradle.kts` — add core dependency (already present, but need branch repos)
- `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiAgent.kt` — implement branch methods + modify send flow
- `modules/domain/ai-agent/build.gradle.kts` — add branch repo dependencies
- `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt` — wire new repos + BranchingContextManager
- `modules/presentation/app/build.gradle.kts` — add new data module dependencies
- `settings.gradle.kts` — include new data modules

### UI files (per variant, in separate worktrees)
- `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt` — add branch intents/state
- `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt` — handle branch operations
- `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatComponent.kt` — expose branch functions
- `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt` — branch UI (variant-specific)
- `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/settings/SessionSettingsContent.kt` — add Branching radio option

---

## Task 1: Core Domain Models — BranchId and Branch

**Files:**
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchId.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/branch/Branch.kt`

- [ ] **Step 1: Create BranchId value class**

Create file `modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchId.kt`:

```kotlin
package com.ai.challenge.core.branch

import java.util.UUID

@JvmInline
value class BranchId(val value: String) {
    companion object {
        fun generate(): BranchId = BranchId(value = UUID.randomUUID().toString())
    }
}
```

- [ ] **Step 2: Create Branch data class**

Create file `modules/core/src/main/kotlin/com/ai/challenge/core/branch/Branch.kt`:

```kotlin
package com.ai.challenge.core.branch

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import kotlin.time.Instant

data class Branch(
    val id: BranchId,
    val sessionId: AgentSessionId,
    val name: String,
    val parentTurnId: TurnId?,
    val isActive: Boolean,
    val createdAt: Instant,
) {
    val isMain: Boolean get() = parentTurnId == null
}
```

- [ ] **Step 3: Add Branching to ContextManagementType**

Modify `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementType.kt`:

```kotlin
package com.ai.challenge.core.context

sealed interface ContextManagementType {
    data object None : ContextManagementType
    data object SummarizeOnThreshold : ContextManagementType
    data object Branching : ContextManagementType
}
```

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew :modules:core:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/branch/ modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementType.kt
git commit -m "feat(core): add BranchId, Branch model and Branching context type"
```

---

## Task 2: Core Repository Interfaces — BranchRepository and BranchTurnRepository

**Files:**
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchRepository.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchTurnRepository.kt`

- [ ] **Step 1: Create BranchRepository interface**

Create file `modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchRepository.kt`:

```kotlin
package com.ai.challenge.core.branch

import com.ai.challenge.core.session.AgentSessionId

interface BranchRepository {
    suspend fun create(branch: Branch): BranchId
    suspend fun get(branchId: BranchId): Branch?
    suspend fun getBySession(sessionId: AgentSessionId): List<Branch>
    suspend fun getMainBranch(sessionId: AgentSessionId): Branch?
    suspend fun getActiveBranch(sessionId: AgentSessionId): Branch?
    suspend fun setActive(sessionId: AgentSessionId, branchId: BranchId)
    suspend fun delete(branchId: BranchId)
}
```

- [ ] **Step 2: Create BranchTurnRepository interface**

Create file `modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchTurnRepository.kt`:

```kotlin
package com.ai.challenge.core.branch

import com.ai.challenge.core.turn.TurnId

interface BranchTurnRepository {
    suspend fun append(branchId: BranchId, turnId: TurnId, orderIndex: Int)
    suspend fun getTurnIds(branchId: BranchId): List<TurnId>
    suspend fun findBranchByTurnId(turnId: TurnId): BranchId?
    suspend fun getMaxOrderIndex(branchId: BranchId): Int?
    suspend fun deleteByBranch(branchId: BranchId)
}
```

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew :modules:core:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchRepository.kt modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchTurnRepository.kt
git commit -m "feat(core): add BranchRepository and BranchTurnRepository interfaces"
```

---

## Task 3: Data Layer — ExposedBranchRepository

**Files:**
- Create: `modules/data/branch-repository-exposed/build.gradle.kts`
- Create: `modules/data/branch-repository-exposed/src/main/kotlin/com/ai/challenge/branch/repository/BranchesTable.kt`
- Create: `modules/data/branch-repository-exposed/src/main/kotlin/com/ai/challenge/branch/repository/ExposedBranchRepository.kt`
- Create: `modules/data/branch-repository-exposed/src/main/kotlin/com/ai/challenge/branch/repository/DatabaseFactory.kt`
- Modify: `settings.gradle.kts` — add module include
- Test: `modules/data/branch-repository-exposed/src/test/kotlin/com/ai/challenge/branch/repository/ExposedBranchRepositoryTest.kt`

- [ ] **Step 1: Add module to settings.gradle.kts**

Add to `settings.gradle.kts` after the existing data modules (after line `include(":modules:data:context-management-repository-exposed")`):

```kotlin
include(":modules:data:branch-repository-exposed")
```

- [ ] **Step 2: Create build.gradle.kts**

Create file `modules/data/branch-repository-exposed/build.gradle.kts`:

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

- [ ] **Step 3: Create BranchesTable**

Create file `modules/data/branch-repository-exposed/src/main/kotlin/com/ai/challenge/branch/repository/BranchesTable.kt`:

```kotlin
package com.ai.challenge.branch.repository

import org.jetbrains.exposed.sql.Table

object BranchesTable : Table("branches") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val name = varchar("name", 255)
    val parentTurnId = varchar("parent_turn_id", 36).nullable()
    val isActive = bool("is_active")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
```

- [ ] **Step 4: Create DatabaseFactory**

Create file `modules/data/branch-repository-exposed/src/main/kotlin/com/ai/challenge/branch/repository/DatabaseFactory.kt`:

```kotlin
package com.ai.challenge.branch.repository

import org.jetbrains.exposed.sql.Database
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

fun createBranchDatabase(): Database {
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

- [ ] **Step 5: Write the failing test**

Create file `modules/data/branch-repository-exposed/src/test/kotlin/com/ai/challenge/branch/repository/ExposedBranchRepositoryTest.kt`:

```kotlin
package com.ai.challenge.branch.repository

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class ExposedBranchRepositoryTest {

    private lateinit var repository: ExposedBranchRepository

    @BeforeTest
    fun setUp() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_branch_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        repository = ExposedBranchRepository(database = db)
    }

    @Test
    fun `create and get round-trip`() = runTest {
        val branch = Branch(
            id = BranchId.generate(),
            sessionId = AgentSessionId(value = "s1"),
            name = "main",
            parentTurnId = null,
            isActive = true,
            createdAt = Clock.System.now(),
        )
        repository.create(branch = branch)

        val result = repository.get(branchId = branch.id)
        assertNotNull(result)
        assertEquals("main", result.name)
        assertNull(result.parentTurnId)
        assertTrue(result.isActive)
        assertTrue(result.isMain)
    }

    @Test
    fun `getBySession returns branches for session`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val main = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            name = "main",
            parentTurnId = null,
            isActive = true,
            createdAt = Clock.System.now(),
        )
        val child = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            name = "experiment",
            parentTurnId = TurnId(value = "t1"),
            isActive = false,
            createdAt = Clock.System.now(),
        )
        repository.create(branch = main)
        repository.create(branch = child)

        val result = repository.getBySession(sessionId = sessionId)
        assertEquals(2, result.size)
    }

    @Test
    fun `getMainBranch returns branch with null parentTurnId`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val main = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            name = "main",
            parentTurnId = null,
            isActive = true,
            createdAt = Clock.System.now(),
        )
        repository.create(branch = main)

        val result = repository.getMainBranch(sessionId = sessionId)
        assertNotNull(result)
        assertEquals(main.id, result.id)
    }

    @Test
    fun `getActiveBranch returns branch with isActive true`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val main = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            name = "main",
            parentTurnId = null,
            isActive = true,
            createdAt = Clock.System.now(),
        )
        repository.create(branch = main)

        val result = repository.getActiveBranch(sessionId = sessionId)
        assertNotNull(result)
        assertEquals(main.id, result.id)
    }

    @Test
    fun `setActive switches active branch`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val main = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            name = "main",
            parentTurnId = null,
            isActive = true,
            createdAt = Clock.System.now(),
        )
        val child = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            name = "experiment",
            parentTurnId = TurnId(value = "t1"),
            isActive = false,
            createdAt = Clock.System.now(),
        )
        repository.create(branch = main)
        repository.create(branch = child)

        repository.setActive(sessionId = sessionId, branchId = child.id)

        val active = repository.getActiveBranch(sessionId = sessionId)
        assertNotNull(active)
        assertEquals(child.id, active.id)

        val mainAfter = repository.get(branchId = main.id)
        assertNotNull(mainAfter)
        assertEquals(false, mainAfter.isActive)
    }

    @Test
    fun `delete removes branch`() = runTest {
        val branch = Branch(
            id = BranchId.generate(),
            sessionId = AgentSessionId(value = "s1"),
            name = "experiment",
            parentTurnId = TurnId(value = "t1"),
            isActive = false,
            createdAt = Clock.System.now(),
        )
        repository.create(branch = branch)
        repository.delete(branchId = branch.id)

        assertNull(repository.get(branchId = branch.id))
    }

    @Test
    fun `get returns null for unknown id`() = runTest {
        assertNull(repository.get(branchId = BranchId(value = "nonexistent")))
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./gradlew :modules:data:branch-repository-exposed:test`
Expected: FAIL — `ExposedBranchRepository` class not found

- [ ] **Step 7: Implement ExposedBranchRepository**

Create file `modules/data/branch-repository-exposed/src/main/kotlin/com/ai/challenge/branch/repository/ExposedBranchRepository.kt`:

```kotlin
package com.ai.challenge.branch.repository

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.BranchRepository
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.time.Instant

class ExposedBranchRepository(
    private val database: Database,
) : BranchRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(BranchesTable)
        }
    }

    override suspend fun create(branch: Branch): BranchId {
        transaction(database) {
            BranchesTable.insert {
                it[id] = branch.id.value
                it[sessionId] = branch.sessionId.value
                it[name] = branch.name
                it[parentTurnId] = branch.parentTurnId?.value
                it[isActive] = branch.isActive
                it[createdAt] = branch.createdAt.toEpochMilliseconds()
            }
        }
        return branch.id
    }

    override suspend fun get(branchId: BranchId): Branch? = transaction(database) {
        BranchesTable.selectAll()
            .where { BranchesTable.id eq branchId.value }
            .singleOrNull()
            ?.toBranch()
    }

    override suspend fun getBySession(sessionId: AgentSessionId): List<Branch> = transaction(database) {
        BranchesTable.selectAll()
            .where { BranchesTable.sessionId eq sessionId.value }
            .map { it.toBranch() }
    }

    override suspend fun getMainBranch(sessionId: AgentSessionId): Branch? = transaction(database) {
        BranchesTable.selectAll()
            .where { (BranchesTable.sessionId eq sessionId.value) and BranchesTable.parentTurnId.isNull() }
            .singleOrNull()
            ?.toBranch()
    }

    override suspend fun getActiveBranch(sessionId: AgentSessionId): Branch? = transaction(database) {
        BranchesTable.selectAll()
            .where { (BranchesTable.sessionId eq sessionId.value) and (BranchesTable.isActive eq true) }
            .singleOrNull()
            ?.toBranch()
    }

    override suspend fun setActive(sessionId: AgentSessionId, branchId: BranchId) {
        transaction(database) {
            BranchesTable.update(where = { BranchesTable.sessionId eq sessionId.value }) {
                it[isActive] = false
            }
            BranchesTable.update(where = { BranchesTable.id eq branchId.value }) {
                it[isActive] = true
            }
        }
    }

    override suspend fun delete(branchId: BranchId) {
        transaction(database) {
            BranchesTable.deleteWhere { BranchesTable.id eq branchId.value }
        }
    }

    private fun ResultRow.toBranch(): Branch = Branch(
        id = BranchId(value = this[BranchesTable.id]),
        sessionId = AgentSessionId(value = this[BranchesTable.sessionId]),
        name = this[BranchesTable.name],
        parentTurnId = this[BranchesTable.parentTurnId]?.let { TurnId(value = it) },
        isActive = this[BranchesTable.isActive],
        createdAt = Instant.fromEpochMilliseconds(this[BranchesTable.createdAt]),
    )
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew :modules:data:branch-repository-exposed:test`
Expected: All 7 tests PASS

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts modules/data/branch-repository-exposed/
git commit -m "feat(data): add ExposedBranchRepository with tests"
```

---

## Task 4: Data Layer — ExposedBranchTurnRepository

**Files:**
- Create: `modules/data/branch-turn-repository-exposed/build.gradle.kts`
- Create: `modules/data/branch-turn-repository-exposed/src/main/kotlin/com/ai/challenge/branch/turn/repository/BranchTurnsTable.kt`
- Create: `modules/data/branch-turn-repository-exposed/src/main/kotlin/com/ai/challenge/branch/turn/repository/ExposedBranchTurnRepository.kt`
- Create: `modules/data/branch-turn-repository-exposed/src/main/kotlin/com/ai/challenge/branch/turn/repository/DatabaseFactory.kt`
- Modify: `settings.gradle.kts` — add module include
- Test: `modules/data/branch-turn-repository-exposed/src/test/kotlin/com/ai/challenge/branch/turn/repository/ExposedBranchTurnRepositoryTest.kt`

- [ ] **Step 1: Add module to settings.gradle.kts**

Add after the branch-repository-exposed include:

```kotlin
include(":modules:data:branch-turn-repository-exposed")
```

- [ ] **Step 2: Create build.gradle.kts**

Create file `modules/data/branch-turn-repository-exposed/build.gradle.kts`:

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

- [ ] **Step 3: Create BranchTurnsTable**

Create file `modules/data/branch-turn-repository-exposed/src/main/kotlin/com/ai/challenge/branch/turn/repository/BranchTurnsTable.kt`:

```kotlin
package com.ai.challenge.branch.turn.repository

import org.jetbrains.exposed.sql.Table

object BranchTurnsTable : Table("branch_turns") {
    val branchId = varchar("branch_id", 36)
    val turnId = varchar("turn_id", 36)
    val orderIndex = integer("order_index")

    override val primaryKey = PrimaryKey(branchId, turnId)
}
```

- [ ] **Step 4: Create DatabaseFactory**

Create file `modules/data/branch-turn-repository-exposed/src/main/kotlin/com/ai/challenge/branch/turn/repository/DatabaseFactory.kt`:

```kotlin
package com.ai.challenge.branch.turn.repository

import org.jetbrains.exposed.sql.Database
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

fun createBranchTurnDatabase(): Database {
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

- [ ] **Step 5: Write the failing test**

Create file `modules/data/branch-turn-repository-exposed/src/test/kotlin/com/ai/challenge/branch/turn/repository/ExposedBranchTurnRepositoryTest.kt`:

```kotlin
package com.ai.challenge.branch.turn.repository

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.turn.TurnId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExposedBranchTurnRepositoryTest {

    private lateinit var repository: ExposedBranchTurnRepository

    @BeforeTest
    fun setUp() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_branch_turn_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        repository = ExposedBranchTurnRepository(database = db)
    }

    @Test
    fun `append and getTurnIds round-trip`() = runTest {
        val branchId = BranchId(value = "b1")
        repository.append(branchId = branchId, turnId = TurnId(value = "t1"), orderIndex = 0)
        repository.append(branchId = branchId, turnId = TurnId(value = "t2"), orderIndex = 1)

        val result = repository.getTurnIds(branchId = branchId)
        assertEquals(listOf(TurnId(value = "t1"), TurnId(value = "t2")), result)
    }

    @Test
    fun `getTurnIds returns ordered by orderIndex`() = runTest {
        val branchId = BranchId(value = "b1")
        repository.append(branchId = branchId, turnId = TurnId(value = "t2"), orderIndex = 1)
        repository.append(branchId = branchId, turnId = TurnId(value = "t1"), orderIndex = 0)

        val result = repository.getTurnIds(branchId = branchId)
        assertEquals(listOf(TurnId(value = "t1"), TurnId(value = "t2")), result)
    }

    @Test
    fun `findBranchByTurnId returns correct branch`() = runTest {
        val branchId = BranchId(value = "b1")
        repository.append(branchId = branchId, turnId = TurnId(value = "t1"), orderIndex = 0)

        val result = repository.findBranchByTurnId(turnId = TurnId(value = "t1"))
        assertEquals(BranchId(value = "b1"), result)
    }

    @Test
    fun `findBranchByTurnId returns null for unknown turn`() = runTest {
        assertNull(repository.findBranchByTurnId(turnId = TurnId(value = "nonexistent")))
    }

    @Test
    fun `getMaxOrderIndex returns max index`() = runTest {
        val branchId = BranchId(value = "b1")
        repository.append(branchId = branchId, turnId = TurnId(value = "t1"), orderIndex = 0)
        repository.append(branchId = branchId, turnId = TurnId(value = "t2"), orderIndex = 1)

        assertEquals(1, repository.getMaxOrderIndex(branchId = branchId))
    }

    @Test
    fun `getMaxOrderIndex returns null for empty branch`() = runTest {
        assertNull(repository.getMaxOrderIndex(branchId = BranchId(value = "empty")))
    }

    @Test
    fun `deleteByBranch removes all mappings`() = runTest {
        val branchId = BranchId(value = "b1")
        repository.append(branchId = branchId, turnId = TurnId(value = "t1"), orderIndex = 0)
        repository.append(branchId = branchId, turnId = TurnId(value = "t2"), orderIndex = 1)

        repository.deleteByBranch(branchId = branchId)

        assertEquals(emptyList(), repository.getTurnIds(branchId = branchId))
    }

    @Test
    fun `getTurnIds returns empty for unknown branch`() = runTest {
        assertEquals(emptyList(), repository.getTurnIds(branchId = BranchId(value = "unknown")))
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./gradlew :modules:data:branch-turn-repository-exposed:test`
Expected: FAIL — `ExposedBranchTurnRepository` class not found

- [ ] **Step 7: Implement ExposedBranchTurnRepository**

Create file `modules/data/branch-turn-repository-exposed/src/main/kotlin/com/ai/challenge/branch/turn/repository/ExposedBranchTurnRepository.kt`:

```kotlin
package com.ai.challenge.branch.turn.repository

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.BranchTurnRepository
import com.ai.challenge.core.turn.TurnId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedBranchTurnRepository(
    private val database: Database,
) : BranchTurnRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(BranchTurnsTable)
        }
    }

    override suspend fun append(branchId: BranchId, turnId: TurnId, orderIndex: Int) {
        transaction(database) {
            BranchTurnsTable.insert {
                it[BranchTurnsTable.branchId] = branchId.value
                it[BranchTurnsTable.turnId] = turnId.value
                it[BranchTurnsTable.orderIndex] = orderIndex
            }
        }
    }

    override suspend fun getTurnIds(branchId: BranchId): List<TurnId> = transaction(database) {
        BranchTurnsTable.selectAll()
            .where { BranchTurnsTable.branchId eq branchId.value }
            .orderBy(BranchTurnsTable.orderIndex, SortOrder.ASC)
            .map { TurnId(value = it[BranchTurnsTable.turnId]) }
    }

    override suspend fun findBranchByTurnId(turnId: TurnId): BranchId? = transaction(database) {
        BranchTurnsTable.selectAll()
            .where { BranchTurnsTable.turnId eq turnId.value }
            .singleOrNull()
            ?.let { BranchId(value = it[BranchTurnsTable.branchId]) }
    }

    override suspend fun getMaxOrderIndex(branchId: BranchId): Int? = transaction(database) {
        BranchTurnsTable.selectAll()
            .where { BranchTurnsTable.branchId eq branchId.value }
            .maxByOrNull { it[BranchTurnsTable.orderIndex] }
            ?.get(BranchTurnsTable.orderIndex)
    }

    override suspend fun deleteByBranch(branchId: BranchId) {
        transaction(database) {
            BranchTurnsTable.deleteWhere { BranchTurnsTable.branchId eq branchId.value }
        }
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew :modules:data:branch-turn-repository-exposed:test`
Expected: All 8 tests PASS

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts modules/data/branch-turn-repository-exposed/
git commit -m "feat(data): add ExposedBranchTurnRepository with tests"
```

---

## Task 5: Update ContextManagementType Serialization

**Files:**
- Modify: `modules/data/context-management-repository-exposed/src/main/kotlin/com/ai/challenge/context/repository/ExposedContextManagementTypeRepository.kt`

- [ ] **Step 1: Update serialization functions**

In `modules/data/context-management-repository-exposed/src/main/kotlin/com/ai/challenge/context/repository/ExposedContextManagementTypeRepository.kt`, replace the two private functions at the bottom:

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
    is ContextManagementType.Branching -> "branching"
}

private fun String.toContextManagementType(): ContextManagementType = when (this) {
    "none" -> ContextManagementType.None
    "summarize_on_threshold" -> ContextManagementType.SummarizeOnThreshold
    "branching" -> ContextManagementType.Branching
    else -> ContextManagementType.None
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :modules:data:context-management-repository-exposed:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add modules/data/context-management-repository-exposed/
git commit -m "feat(data): add Branching type serialization to context management repository"
```

---

## Task 6: BranchingContextManager with TDD

**Files:**
- Create: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/BranchingContextManager.kt`
- Modify: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt`
- Modify: `modules/domain/context-manager/build.gradle.kts`
- Test: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/BranchingContextManagerTest.kt`

- [ ] **Step 1: Write the failing test**

Create file `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/BranchingContextManagerTest.kt`:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.BranchRepository
import com.ai.challenge.core.branch.BranchTurnRepository
import com.ai.challenge.core.context.ContextManager.PreparedContext.ContextMessage
import com.ai.challenge.core.context.ContextManager.PreparedContext.ContextMessage.MessageRole
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.turn.TurnRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock

class BranchingContextManagerTest {

    private lateinit var turnRepo: InMemoryTurnRepository
    private lateinit var branchRepo: InMemoryBranchRepository
    private lateinit var branchTurnRepo: InMemoryBranchTurnRepository

    @BeforeTest
    fun setup() {
        turnRepo = InMemoryTurnRepository()
        branchRepo = InMemoryBranchRepository()
        branchTurnRepo = InMemoryBranchTurnRepository()
    }

    private fun createManager(): BranchingContextManager =
        BranchingContextManager(
            turnRepository = turnRepo,
            branchRepository = branchRepo,
            branchTurnRepository = branchTurnRepo,
        )

    @Test
    fun `prepareContext for main branch with turns`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val branchId = BranchId(value = "b-main")

        branchRepo.create(branch = Branch(
            id = branchId,
            sessionId = sessionId,
            name = "main",
            parentTurnId = null,
            isActive = true,
            createdAt = Clock.System.now(),
        ))

        val t1 = Turn(id = TurnId(value = "t1"), userMessage = "hi", agentResponse = "hello")
        val t2 = Turn(id = TurnId(value = "t2"), userMessage = "how", agentResponse = "fine")
        turnRepo.append(sessionId = sessionId, turn = t1)
        turnRepo.append(sessionId = sessionId, turn = t2)
        branchTurnRepo.append(branchId = branchId, turnId = t1.id, orderIndex = 0)
        branchTurnRepo.append(branchId = branchId, turnId = t2.id, orderIndex = 1)

        val result = createManager().prepareContext(sessionId = sessionId, newMessage = "what?")

        assertFalse(result.compressed)
        assertEquals(2, result.originalTurnCount)
        assertEquals(2, result.retainedTurnCount)
        assertEquals(5, result.messages.size)
        assertEquals(ContextMessage(role = MessageRole.User, content = "hi"), result.messages[0])
        assertEquals(ContextMessage(role = MessageRole.Assistant, content = "hello"), result.messages[1])
        assertEquals(ContextMessage(role = MessageRole.User, content = "what?"), result.messages[4])
    }

    @Test
    fun `prepareContext for child branch includes trunk`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val mainId = BranchId(value = "b-main")
        val childId = BranchId(value = "b-child")

        branchRepo.create(branch = Branch(
            id = mainId,
            sessionId = sessionId,
            name = "main",
            parentTurnId = null,
            isActive = false,
            createdAt = Clock.System.now(),
        ))

        val t1 = Turn(id = TurnId(value = "t1"), userMessage = "msg1", agentResponse = "resp1")
        val t2 = Turn(id = TurnId(value = "t2"), userMessage = "msg2", agentResponse = "resp2")
        val t3 = Turn(id = TurnId(value = "t3"), userMessage = "msg3", agentResponse = "resp3")
        turnRepo.append(sessionId = sessionId, turn = t1)
        turnRepo.append(sessionId = sessionId, turn = t2)
        turnRepo.append(sessionId = sessionId, turn = t3)
        branchTurnRepo.append(branchId = mainId, turnId = t1.id, orderIndex = 0)
        branchTurnRepo.append(branchId = mainId, turnId = t2.id, orderIndex = 1)
        branchTurnRepo.append(branchId = mainId, turnId = t3.id, orderIndex = 2)

        branchRepo.create(branch = Branch(
            id = childId,
            sessionId = sessionId,
            name = "experiment",
            parentTurnId = TurnId(value = "t2"),
            isActive = true,
            createdAt = Clock.System.now(),
        ))

        val t4 = Turn(id = TurnId(value = "t4"), userMessage = "branch-msg", agentResponse = "branch-resp")
        turnRepo.append(sessionId = sessionId, turn = t4)
        branchTurnRepo.append(branchId = childId, turnId = t4.id, orderIndex = 0)

        val result = createManager().prepareContext(sessionId = sessionId, newMessage = "new")

        assertEquals(3, result.originalTurnCount)
        assertEquals(3, result.retainedTurnCount)
        assertEquals(7, result.messages.size)
        assertEquals(ContextMessage(role = MessageRole.User, content = "msg1"), result.messages[0])
        assertEquals(ContextMessage(role = MessageRole.Assistant, content = "resp1"), result.messages[1])
        assertEquals(ContextMessage(role = MessageRole.User, content = "msg2"), result.messages[2])
        assertEquals(ContextMessage(role = MessageRole.Assistant, content = "resp2"), result.messages[3])
        assertEquals(ContextMessage(role = MessageRole.User, content = "branch-msg"), result.messages[4])
        assertEquals(ContextMessage(role = MessageRole.Assistant, content = "branch-resp"), result.messages[5])
        assertEquals(ContextMessage(role = MessageRole.User, content = "new"), result.messages[6])
    }

    @Test
    fun `prepareContext for branch of branch includes full path`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val mainId = BranchId(value = "b-main")
        val childId = BranchId(value = "b-child")
        val grandchildId = BranchId(value = "b-grandchild")

        branchRepo.create(branch = Branch(
            id = mainId, sessionId = sessionId, name = "main",
            parentTurnId = null, isActive = false, createdAt = Clock.System.now(),
        ))
        branchRepo.create(branch = Branch(
            id = childId, sessionId = sessionId, name = "child",
            parentTurnId = TurnId(value = "t1"), isActive = false, createdAt = Clock.System.now(),
        ))
        branchRepo.create(branch = Branch(
            id = grandchildId, sessionId = sessionId, name = "grandchild",
            parentTurnId = TurnId(value = "t2"), isActive = true, createdAt = Clock.System.now(),
        ))

        val t1 = Turn(id = TurnId(value = "t1"), userMessage = "m1", agentResponse = "r1")
        val t2 = Turn(id = TurnId(value = "t2"), userMessage = "m2", agentResponse = "r2")
        val t3 = Turn(id = TurnId(value = "t3"), userMessage = "m3", agentResponse = "r3")
        turnRepo.append(sessionId = sessionId, turn = t1)
        turnRepo.append(sessionId = sessionId, turn = t2)
        turnRepo.append(sessionId = sessionId, turn = t3)

        branchTurnRepo.append(branchId = mainId, turnId = t1.id, orderIndex = 0)
        branchTurnRepo.append(branchId = childId, turnId = t2.id, orderIndex = 0)
        branchTurnRepo.append(branchId = grandchildId, turnId = t3.id, orderIndex = 0)

        val result = createManager().prepareContext(sessionId = sessionId, newMessage = "deep")

        assertEquals(3, result.originalTurnCount)
        assertEquals(7, result.messages.size)
        assertEquals("m1", result.messages[0].content)
        assertEquals("r1", result.messages[1].content)
        assertEquals("m2", result.messages[2].content)
        assertEquals("r2", result.messages[3].content)
        assertEquals("m3", result.messages[4].content)
        assertEquals("r3", result.messages[5].content)
        assertEquals("deep", result.messages[6].content)
    }

    @Test
    fun `prepareContext for empty branch with trunk`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        val mainId = BranchId(value = "b-main")
        val childId = BranchId(value = "b-child")

        branchRepo.create(branch = Branch(
            id = mainId, sessionId = sessionId, name = "main",
            parentTurnId = null, isActive = false, createdAt = Clock.System.now(),
        ))
        branchRepo.create(branch = Branch(
            id = childId, sessionId = sessionId, name = "empty",
            parentTurnId = TurnId(value = "t1"), isActive = true, createdAt = Clock.System.now(),
        ))

        val t1 = Turn(id = TurnId(value = "t1"), userMessage = "m1", agentResponse = "r1")
        turnRepo.append(sessionId = sessionId, turn = t1)
        branchTurnRepo.append(branchId = mainId, turnId = t1.id, orderIndex = 0)

        val result = createManager().prepareContext(sessionId = sessionId, newMessage = "start")

        assertEquals(1, result.originalTurnCount)
        assertEquals(3, result.messages.size)
        assertEquals("m1", result.messages[0].content)
        assertEquals("r1", result.messages[1].content)
        assertEquals("start", result.messages[2].content)
    }
}

private class InMemoryBranchRepository : BranchRepository {
    private val store = mutableListOf<Branch>()

    override suspend fun create(branch: Branch): BranchId {
        store.add(branch)
        return branch.id
    }

    override suspend fun get(branchId: BranchId): Branch? =
        store.firstOrNull { it.id == branchId }

    override suspend fun getBySession(sessionId: AgentSessionId): List<Branch> =
        store.filter { it.sessionId == sessionId }

    override suspend fun getMainBranch(sessionId: AgentSessionId): Branch? =
        store.firstOrNull { it.sessionId == sessionId && it.isMain }

    override suspend fun getActiveBranch(sessionId: AgentSessionId): Branch? =
        store.firstOrNull { it.sessionId == sessionId && it.isActive }

    override suspend fun setActive(sessionId: AgentSessionId, branchId: BranchId) {
        store.replaceAll { branch ->
            if (branch.sessionId == sessionId) branch.copy(isActive = branch.id == branchId)
            else branch
        }
    }

    override suspend fun delete(branchId: BranchId) {
        store.removeAll { it.id == branchId }
    }
}

private class InMemoryBranchTurnRepository : BranchTurnRepository {
    private val store = mutableListOf<Triple<BranchId, TurnId, Int>>()

    override suspend fun append(branchId: BranchId, turnId: TurnId, orderIndex: Int) {
        store.add(Triple(branchId, turnId, orderIndex))
    }

    override suspend fun getTurnIds(branchId: BranchId): List<TurnId> =
        store.filter { it.first == branchId }.sortedBy { it.third }.map { it.second }

    override suspend fun findBranchByTurnId(turnId: TurnId): BranchId? =
        store.firstOrNull { it.second == turnId }?.first

    override suspend fun getMaxOrderIndex(branchId: BranchId): Int? =
        store.filter { it.first == branchId }.maxByOrNull { it.third }?.third

    override suspend fun deleteByBranch(branchId: BranchId) {
        store.removeAll { it.first == branchId }
    }
}
```

Note: This test file reuses `InMemoryTurnRepository` from `DefaultContextManagerTest.kt`. Copy it into this file or extract to a shared test-fixtures location. For simplicity, copy the same implementation into this file.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :modules:domain:context-manager:test --tests "com.ai.challenge.context.BranchingContextManagerTest"`
Expected: FAIL — `BranchingContextManager` not found

- [ ] **Step 3: Implement BranchingContextManager**

Create file `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/BranchingContextManager.kt`:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.BranchRepository
import com.ai.challenge.core.branch.BranchTurnRepository
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.ContextManager.PreparedContext
import com.ai.challenge.core.context.ContextManager.PreparedContext.ContextMessage
import com.ai.challenge.core.context.ContextManager.PreparedContext.ContextMessage.MessageRole
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnRepository

class BranchingContextManager(
    private val turnRepository: TurnRepository,
    private val branchRepository: BranchRepository,
    private val branchTurnRepository: BranchTurnRepository,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        newMessage: String,
    ): PreparedContext {
        val activeBranch = branchRepository.getActiveBranch(sessionId = sessionId)
            ?: error("No active branch for session ${sessionId.value}")
        val turns = collectBranchPath(branchId = activeBranch.id)
        val messages = turnsToMessages(turns = turns) +
            ContextMessage(role = MessageRole.User, content = newMessage)
        return PreparedContext(
            messages = messages,
            compressed = false,
            originalTurnCount = turns.size,
            retainedTurnCount = turns.size,
            summaryCount = 0,
        )
    }

    private suspend fun collectBranchPath(branchId: BranchId): List<Turn> {
        val branch = branchRepository.get(branchId = branchId)
            ?: error("Branch ${branchId.value} not found")
        val myTurnIds = branchTurnRepository.getTurnIds(branchId = branchId)
        val myTurns = myTurnIds.mapNotNull { turnRepository.get(turnId = it) }

        if (branch.parentTurnId == null) {
            return myTurns
        }

        val parentBranchId = branchTurnRepository.findBranchByTurnId(turnId = branch.parentTurnId)
            ?: error("Could not find branch owning turn ${branch.parentTurnId.value}")
        val parentPath = collectBranchPath(branchId = parentBranchId)

        val cutIndex = parentPath.indexOfFirst { it.id == branch.parentTurnId }
        val trunk = if (cutIndex >= 0) parentPath.subList(fromIndex = 0, toIndex = cutIndex + 1) else parentPath

        return trunk + myTurns
    }

    private fun turnsToMessages(turns: List<Turn>): List<ContextMessage> =
        turns.flatMap {
            listOf(
                ContextMessage(role = MessageRole.User, content = it.userMessage),
                ContextMessage(role = MessageRole.Assistant, content = it.agentResponse),
            )
        }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :modules:domain:context-manager:test --tests "com.ai.challenge.context.BranchingContextManagerTest"`
Expected: All 4 tests PASS

- [ ] **Step 5: Integrate into DefaultContextManager**

Modify `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt`.

Add constructor parameter and import:

```kotlin
import com.ai.challenge.core.context.ContextManagementType

class DefaultContextManager(
    private val contextManagementRepository: ContextManagementTypeRepository,
    private val compressor: ContextCompressor,
    private val summaryRepository: SummaryRepository,
    private val turnRepository: TurnRepository,
    private val branchingContextManager: BranchingContextManager,
) : ContextManager {
```

Update the `when` in `prepareContext`:

```kotlin
return when (type) {
    is ContextManagementType.None -> passThrough(sessionId = sessionId, newMessage = newMessage)
    is ContextManagementType.SummarizeOnThreshold -> summarizeOnThreshold(
        sessionId = sessionId,
        newMessage = newMessage,
    )
    is ContextManagementType.Branching -> branchingContextManager.prepareContext(
        sessionId = sessionId,
        newMessage = newMessage,
    )
}
```

- [ ] **Step 6: Fix DefaultContextManagerTest — update createManager call**

In `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt`, update `createManager()`:

```kotlin
private fun createManager(): DefaultContextManager =
    DefaultContextManager(
        contextManagementRepository = fakeContextManagementRepo,
        compressor = fakeCompressor,
        summaryRepository = fakeSummaryRepo,
        turnRepository = fakeTurnRepo,
        branchingContextManager = BranchingContextManager(
            turnRepository = fakeTurnRepo,
            branchRepository = InMemoryBranchRepository(),
            branchTurnRepository = InMemoryBranchTurnRepository(),
        ),
    )
```

Add the same `InMemoryBranchRepository` and `InMemoryBranchTurnRepository` fakes from `BranchingContextManagerTest.kt` to this test file (or extract to shared test-fixtures).

- [ ] **Step 7: Run all context-manager tests**

Run: `./gradlew :modules:domain:context-manager:test`
Expected: All tests PASS (existing + new)

- [ ] **Step 8: Commit**

```bash
git add modules/domain/context-manager/
git commit -m "feat(context-manager): add BranchingContextManager with delegation from DefaultContextManager"
```

---

## Task 7: Agent Interface and AiAgent Implementation

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/agent/Agent.kt`
- Modify: `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiAgent.kt`
- Modify: `modules/domain/ai-agent/build.gradle.kts`

- [ ] **Step 1: Add branch methods to Agent interface**

Modify `modules/core/src/main/kotlin/com/ai/challenge/core/agent/Agent.kt`. Add imports and new methods:

```kotlin
package com.ai.challenge.core.agent

import arrow.core.Either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.cost.CostDetails
import com.ai.challenge.core.token.TokenDetails
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId

interface Agent {
    suspend fun send(sessionId: AgentSessionId, message: String): Either<AgentError, AgentResponse>
    suspend fun createSession(title: String = ""): AgentSessionId
    suspend fun deleteSession(id: AgentSessionId): Boolean
    suspend fun listSessions(): List<AgentSession>
    suspend fun getSession(id: AgentSessionId): AgentSession?
    suspend fun updateSessionTitle(id: AgentSessionId, title: String)
    suspend fun getTurns(sessionId: AgentSessionId, limit: Int? = null): List<Turn>
    suspend fun getTokensByTurn(turnId: TurnId): TokenDetails?
    suspend fun getTokensBySession(sessionId: AgentSessionId): Map<TurnId, TokenDetails>
    suspend fun getSessionTotalTokens(sessionId: AgentSessionId): TokenDetails
    suspend fun getCostByTurn(turnId: TurnId): CostDetails?
    suspend fun getCostBySession(sessionId: AgentSessionId): Map<TurnId, CostDetails>
    suspend fun getSessionTotalCost(sessionId: AgentSessionId): CostDetails
    suspend fun getContextManagementType(sessionId: AgentSessionId): Either<AgentError, ContextManagementType>
    suspend fun updateContextManagementType(sessionId: AgentSessionId, type: ContextManagementType): Either<AgentError, Unit>

    suspend fun createBranch(sessionId: AgentSessionId, name: String, parentTurnId: TurnId): Either<AgentError, BranchId>
    suspend fun deleteBranch(branchId: BranchId): Either<AgentError, Unit>
    suspend fun getBranches(sessionId: AgentSessionId): Either<AgentError, List<Branch>>
    suspend fun switchBranch(sessionId: AgentSessionId, branchId: BranchId): Either<AgentError, Unit>
    suspend fun getActiveBranch(sessionId: AgentSessionId): Either<AgentError, Branch?>
}
```

- [ ] **Step 2: Add dependencies to ai-agent build.gradle.kts**

Modify `modules/domain/ai-agent/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":modules:data:open-router-service"))
    implementation(project(":modules:core"))
    implementation(libs.arrow.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
}
```

(No new dependencies needed — AiAgent accesses repos through core interfaces.)

- [ ] **Step 3: Implement branch methods in AiAgent**

Modify `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiAgent.kt`. Add new constructor params and imports:

```kotlin
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.BranchRepository
import com.ai.challenge.core.branch.BranchTurnRepository

class AiAgent(
    private val service: OpenRouterService,
    private val model: String,
    private val sessionRepository: AgentSessionRepository,
    private val turnRepository: TurnRepository,
    private val tokenRepository: TokenDetailsRepository,
    private val costRepository: CostDetailsRepository,
    private val contextManager: ContextManager,
    private val contextManagementRepository: ContextManagementTypeRepository,
    private val branchRepository: BranchRepository,
    private val branchTurnRepository: BranchTurnRepository,
) : Agent {
```

Add the `send` flow modification — after creating and appending the Turn, check if branching is active and append the branch-turn mapping:

Replace the existing `send` method's turn recording section. After line `val turnId = turnRepository.append(sessionId, turn)`, add:

```kotlin
        val contextType = contextManagementRepository.getBySession(sessionId)
        if (contextType is ContextManagementType.Branching) {
            val activeBranch = branchRepository.getActiveBranch(sessionId = sessionId)
            if (activeBranch != null) {
                val maxIndex = branchTurnRepository.getMaxOrderIndex(branchId = activeBranch.id)
                branchTurnRepository.append(
                    branchId = activeBranch.id,
                    turnId = turnId,
                    orderIndex = (maxIndex ?: -1) + 1,
                )
            }
        }
```

Add new method implementations at the bottom of the class:

```kotlin
    override suspend fun createBranch(
        sessionId: AgentSessionId,
        name: String,
        parentTurnId: TurnId,
    ): Either<AgentError, BranchId> = either {
        val type = contextManagementRepository.getBySession(sessionId)
        if (type !is ContextManagementType.Branching) {
            raise(AgentError.ApiError("Branching is not enabled for this session"))
        }
        val branch = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            name = name,
            parentTurnId = parentTurnId,
            isActive = false,
            createdAt = Clock.System.now(),
        )
        branchRepository.create(branch = branch)
    }

    override suspend fun deleteBranch(branchId: BranchId): Either<AgentError, Unit> = either {
        val branch = branchRepository.get(branchId = branchId)
            ?: raise(AgentError.ApiError("Branch not found"))
        if (branch.isMain) {
            raise(AgentError.ApiError("Cannot delete main branch"))
        }
        cascadeDeleteBranch(branchId = branchId, sessionId = branch.sessionId)
    }

    override suspend fun getBranches(sessionId: AgentSessionId): Either<AgentError, List<Branch>> =
        Either.Right(branchRepository.getBySession(sessionId = sessionId))

    override suspend fun switchBranch(
        sessionId: AgentSessionId,
        branchId: BranchId,
    ): Either<AgentError, Unit> = either {
        val branch = branchRepository.get(branchId = branchId)
            ?: raise(AgentError.ApiError("Branch not found"))
        if (branch.sessionId != sessionId) {
            raise(AgentError.ApiError("Branch does not belong to this session"))
        }
        branchRepository.setActive(sessionId = sessionId, branchId = branchId)
    }

    override suspend fun getActiveBranch(sessionId: AgentSessionId): Either<AgentError, Branch?> =
        Either.Right(branchRepository.getActiveBranch(sessionId = sessionId))
```

Add the `cascadeDeleteBranch` private method and the import for `Clock`:

```kotlin
import kotlin.time.Clock

    private suspend fun cascadeDeleteBranch(branchId: BranchId, sessionId: AgentSessionId) {
        val turnIds = branchTurnRepository.getTurnIds(branchId = branchId)
        val allBranches = branchRepository.getBySession(sessionId = sessionId)
        val childBranches = allBranches.filter { it.parentTurnId != null && it.parentTurnId in turnIds.toSet() }

        for (child in childBranches) {
            cascadeDeleteBranch(branchId = child.id, sessionId = sessionId)
        }

        val wasActive = branchRepository.get(branchId = branchId)?.isActive ?: false
        branchTurnRepository.deleteByBranch(branchId = branchId)
        branchRepository.delete(branchId = branchId)

        if (wasActive) {
            val mainBranch = branchRepository.getMainBranch(sessionId = sessionId)
            if (mainBranch != null) {
                branchRepository.setActive(sessionId = sessionId, branchId = mainBranch.id)
            }
        }
    }
```

Also update `updateContextManagementType` to initialize branches when switching to Branching:

```kotlin
    override suspend fun updateContextManagementType(
        sessionId: AgentSessionId,
        type: ContextManagementType,
    ): Either<AgentError, Unit> {
        contextManagementRepository.save(sessionId, type)
        if (type is ContextManagementType.Branching) {
            val existing = branchRepository.getMainBranch(sessionId = sessionId)
            if (existing == null) {
                val mainBranch = Branch(
                    id = BranchId.generate(),
                    sessionId = sessionId,
                    name = "main",
                    parentTurnId = null,
                    isActive = true,
                    createdAt = Clock.System.now(),
                )
                branchRepository.create(branch = mainBranch)
                val turns = turnRepository.getBySession(sessionId = sessionId)
                for ((index, turn) in turns.withIndex()) {
                    branchTurnRepository.append(
                        branchId = mainBranch.id,
                        turnId = turn.id,
                        orderIndex = index,
                    )
                }
            }
        }
        return Either.Right(Unit)
    }
```

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew :modules:domain:ai-agent:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/agent/Agent.kt modules/domain/ai-agent/
git commit -m "feat(agent): add branch management methods to Agent interface and AiAgent"
```

---

## Task 8: Koin DI Wiring

**Files:**
- Modify: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt`
- Modify: `modules/presentation/app/build.gradle.kts`

- [ ] **Step 1: Add new module dependencies to app build.gradle.kts**

Modify `modules/presentation/app/build.gradle.kts`. Add after existing data module lines:

```kotlin
    implementation(project(":modules:data:branch-repository-exposed"))
    implementation(project(":modules:data:branch-turn-repository-exposed"))
```

- [ ] **Step 2: Update AppModule.kt**

Modify `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt`:

Add imports:
```kotlin
import com.ai.challenge.branch.repository.ExposedBranchRepository
import com.ai.challenge.branch.repository.createBranchDatabase
import com.ai.challenge.branch.turn.repository.ExposedBranchTurnRepository
import com.ai.challenge.branch.turn.repository.createBranchTurnDatabase
import com.ai.challenge.context.BranchingContextManager
import com.ai.challenge.core.branch.BranchRepository
import com.ai.challenge.core.branch.BranchTurnRepository
```

Add repository singletons (after existing repository lines):
```kotlin
    single<BranchRepository> { ExposedBranchRepository(database = createBranchDatabase()) }
    single<BranchTurnRepository> { ExposedBranchTurnRepository(database = createBranchTurnDatabase()) }
```

Add BranchingContextManager singleton (before the ContextManager singleton):
```kotlin
    single {
        BranchingContextManager(
            turnRepository = get(),
            branchRepository = get(),
            branchTurnRepository = get(),
        )
    }
```

Update the ContextManager singleton to pass branchingContextManager:
```kotlin
    single<ContextManager> {
        DefaultContextManager(
            contextManagementRepository = get(),
            compressor = get(),
            summaryRepository = get(),
            turnRepository = get(),
            branchingContextManager = get(),
        )
    }
```

Update the Agent singleton to pass branch repos:
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
            contextManagementRepository = get(),
            branchRepository = get(),
            branchTurnRepository = get(),
        )
    }
```

- [ ] **Step 3: Build full project**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add modules/presentation/app/
git commit -m "feat(app): wire BranchRepository, BranchTurnRepository and BranchingContextManager in Koin DI"
```

---

## Task 9: Add Branching Option to Session Settings UI

**Files:**
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/settings/SessionSettingsContent.kt`

- [ ] **Step 1: Add Branching radio option**

In `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/settings/SessionSettingsContent.kt`, add a third `ContextManagementTypeOption` after the "Summarize on threshold" one:

```kotlin
                Spacer(modifier = Modifier.height(4.dp))

                ContextManagementTypeOption(
                    label = "Branching",
                    description = "Create dialog branches from any message",
                    selected = state.currentType is ContextManagementType.Branching,
                    onClick = { component.onChangeType(ContextManagementType.Branching) },
                )
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :modules:presentation:compose-ui:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/settings/SessionSettingsContent.kt
git commit -m "feat(ui): add Branching option to session settings context management selector"
```

---

## Task 10: Create Worktrees for UI Variants

After Task 9, the backend is complete. Now create three worktrees for the three UI navigation variants.

- [ ] **Step 1: Create worktree branches**

```bash
git branch wt-4-ui-tabs
git branch wt-4-ui-dropdown
git branch wt-4-ui-panel
```

- [ ] **Step 2: Create worktrees**

```bash
git worktree add /Users/ilya/IdeaProjects/AiChallenge/.worktrees/wt-4-ui-tabs wt-4-ui-tabs
git worktree add /Users/ilya/IdeaProjects/AiChallenge/.worktrees/wt-4-ui-dropdown wt-4-ui-dropdown
git worktree add /Users/ilya/IdeaProjects/AiChallenge/.worktrees/wt-4-ui-panel wt-4-ui-panel
```

- [ ] **Step 3: Commit**

No commit needed — worktrees are local only.

---

## Task 10A: UI Variant A — Tab Bar (in wt-4-ui-tabs worktree)

**Working directory:** `/Users/ilya/IdeaProjects/AiChallenge/.worktrees/wt-4-ui-tabs`

This task adds branch-aware UI using horizontal tabs above the chat area. All changes are in `modules/presentation/compose-ui/`.

**Files:**
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatComponent.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt`

- [ ] **Step 1: Extend ChatStore with branch state and intents**

Modify `ChatStore.kt`:

```kotlin
package com.ai.challenge.ui.chat.store

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.cost.CostDetails
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.token.TokenDetails
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store

interface ChatStore : Store<ChatStore.Intent, ChatStore.State, Nothing> {

    sealed interface Intent {
        data class SendMessage(val text: String) : Intent
        data class LoadSession(val sessionId: AgentSessionId) : Intent
        data class CreateBranch(val name: String, val parentTurnId: TurnId) : Intent
        data class SwitchBranch(val branchId: BranchId) : Intent
        data class DeleteBranch(val branchId: BranchId) : Intent
        data object LoadBranches : Intent
    }

    data class State(
        val sessionId: AgentSessionId? = null,
        val messages: List<UiMessage> = emptyList(),
        val isLoading: Boolean = false,
        val turnTokens: Map<TurnId, TokenDetails> = emptyMap(),
        val turnCosts: Map<TurnId, CostDetails> = emptyMap(),
        val sessionTokens: TokenDetails = TokenDetails(),
        val sessionCosts: CostDetails = CostDetails(),
        val branches: List<Branch> = emptyList(),
        val activeBranch: Branch? = null,
        val isBranchingEnabled: Boolean = false,
    )
}
```

- [ ] **Step 2: Extend ChatStoreFactory with branch handling**

Modify `ChatStoreFactory.kt`. Add new Msg variants:

```kotlin
        data class BranchesLoaded(
            val branches: List<Branch>,
            val activeBranch: Branch?,
            val isBranchingEnabled: Boolean,
        ) : Msg
        data class BranchSwitched(
            val messages: List<UiMessage>,
            val activeBranch: Branch?,
            val branches: List<Branch>,
            val turnTokens: Map<TurnId, TokenDetails>,
            val turnCosts: Map<TurnId, CostDetails>,
            val sessionTokens: TokenDetails,
            val sessionCosts: CostDetails,
        ) : Msg
```

Add intent handlers in ExecutorImpl:

```kotlin
is ChatStore.Intent.CreateBranch -> handleCreateBranch(name = intent.name, parentTurnId = intent.parentTurnId)
is ChatStore.Intent.SwitchBranch -> handleSwitchBranch(branchId = intent.branchId)
is ChatStore.Intent.DeleteBranch -> handleDeleteBranch(branchId = intent.branchId)
is ChatStore.Intent.LoadBranches -> handleLoadBranches()
```

Implement handlers:

```kotlin
        private fun handleLoadBranches() {
            val sessionId = state().sessionId ?: return
            scope.launch {
                val typeResult = agent.getContextManagementType(sessionId)
                val isBranching = typeResult is Either.Right && typeResult.value is ContextManagementType.Branching
                val branches = if (isBranching) {
                    when (val r = agent.getBranches(sessionId)) {
                        is Either.Right -> r.value
                        is Either.Left -> emptyList()
                    }
                } else emptyList()
                val activeBranch = if (isBranching) {
                    when (val r = agent.getActiveBranch(sessionId)) {
                        is Either.Right -> r.value
                        is Either.Left -> null
                    }
                } else null
                dispatch(Msg.BranchesLoaded(
                    branches = branches,
                    activeBranch = activeBranch,
                    isBranchingEnabled = isBranching,
                ))
            }
        }

        private fun handleCreateBranch(name: String, parentTurnId: TurnId) {
            val sessionId = state().sessionId ?: return
            scope.launch {
                when (agent.createBranch(sessionId = sessionId, name = name, parentTurnId = parentTurnId)) {
                    is Either.Right -> handleLoadBranches()
                    is Either.Left -> {}
                }
            }
        }

        private fun handleSwitchBranch(branchId: BranchId) {
            val sessionId = state().sessionId ?: return
            dispatch(Msg.Loading)
            scope.launch {
                when (agent.switchBranch(sessionId = sessionId, branchId = branchId)) {
                    is Either.Right -> {
                        // Reload turns for the new branch context
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
                        val branches = when (val r = agent.getBranches(sessionId)) {
                            is Either.Right -> r.value
                            is Either.Left -> emptyList()
                        }
                        val activeBranch = when (val r = agent.getActiveBranch(sessionId)) {
                            is Either.Right -> r.value
                            is Either.Left -> null
                        }
                        dispatch(Msg.BranchSwitched(
                            messages = messages,
                            activeBranch = activeBranch,
                            branches = branches,
                            turnTokens = turnTokens,
                            turnCosts = turnCosts,
                            sessionTokens = sessionTokens,
                            sessionCosts = sessionCosts,
                        ))
                    }
                    is Either.Left -> {}
                }
                dispatch(Msg.LoadingComplete)
            }
        }

        private fun handleDeleteBranch(branchId: BranchId) {
            val sessionId = state().sessionId ?: return
            scope.launch {
                when (agent.deleteBranch(branchId = branchId)) {
                    is Either.Right -> handleLoadBranches()
                    is Either.Left -> {}
                }
            }
        }
```

Add after `handleLoadSession` — at the end of `handleLoadSession`, after dispatching `SessionLoaded`, load branches too:

```kotlin
                // At the end of handleLoadSession, after dispatch(Msg.SessionLoaded(...)):
                handleLoadBranches()
```

Add reducer cases:

```kotlin
                is Msg.BranchesLoaded -> copy(
                    branches = msg.branches,
                    activeBranch = msg.activeBranch,
                    isBranchingEnabled = msg.isBranchingEnabled,
                )
                is Msg.BranchSwitched -> copy(
                    messages = msg.messages,
                    activeBranch = msg.activeBranch,
                    branches = msg.branches,
                    turnTokens = msg.turnTokens,
                    turnCosts = msg.turnCosts,
                    sessionTokens = msg.sessionTokens,
                    sessionCosts = msg.sessionCosts,
                )
```

- [ ] **Step 3: Update ChatComponent to expose branch functions**

Modify `ChatComponent.kt`:

```kotlin
    fun onCreateBranch(name: String, parentTurnId: TurnId) {
        store.accept(ChatStore.Intent.CreateBranch(name = name, parentTurnId = parentTurnId))
    }

    fun onSwitchBranch(branchId: BranchId) {
        store.accept(ChatStore.Intent.SwitchBranch(branchId = branchId))
    }

    fun onDeleteBranch(branchId: BranchId) {
        store.accept(ChatStore.Intent.DeleteBranch(branchId = branchId))
    }
```

- [ ] **Step 4: Implement Tab Bar UI in ChatContent.kt**

This is the variant-specific part. Add a `BranchTabBar` composable above the LazyColumn:

```kotlin
@Composable
private fun BranchTabBar(
    branches: List<Branch>,
    activeBranch: Branch?,
    onSwitchBranch: (BranchId) -> Unit,
    onDeleteBranch: (BranchId) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (branch in branches) {
            val isActive = branch.id == activeBranch?.id
            TextButton(
                onClick = { onSwitchBranch(branch.id) },
            ) {
                Text(
                    text = branch.name,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (!branch.isMain) {
                    IconButton(
                        onClick = { onDeleteBranch(branch.id) },
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete branch",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
```

Add hover-based branch creation button on messages — add a "⑂" button that appears on hover on each message bubble. When clicked, show a dialog for naming the branch.

In `ChatContent.kt`, wrap the ChatContent Column. After HorizontalDivider and before LazyColumn, add:

```kotlin
        if (state.isBranchingEnabled && state.branches.isNotEmpty()) {
            BranchTabBar(
                branches = state.branches,
                activeBranch = state.activeBranch,
                onSwitchBranch = { component.onSwitchBranch(it) },
                onDeleteBranch = { component.onDeleteBranch(it) },
            )
            HorizontalDivider()
        }
```

Add a branch creation dialog state and composable. This is a basic `AlertDialog` shown when the user clicks the "⑂" button on a message:

```kotlin
@Composable
private fun CreateBranchDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Branch") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Branch name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.ifBlank { "Branch ${System.currentTimeMillis() % 1000}" }) }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
```

- [ ] **Step 5: Build to verify**

Run: `./gradlew :modules:presentation:compose-ui:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add modules/presentation/compose-ui/
git commit -m "feat(ui): implement Tab Bar branch navigation (Variant A)"
```

---

## Task 10B: UI Variant B — Branch Indicator + Dropdown (in wt-4-ui-dropdown worktree)

**Working directory:** `/Users/ilya/IdeaProjects/AiChallenge/.worktrees/wt-4-ui-dropdown`

Same ChatStore/ChatStoreFactory/ChatComponent changes as Task 10A (Steps 1-3 are identical). Only Step 4 differs — the navigation UI.

- [ ] **Steps 1-3: Same as Task 10A** — Apply identical ChatStore, ChatStoreFactory, and ChatComponent changes.

- [ ] **Step 4: Implement Dropdown UI in ChatContent.kt**

Instead of `BranchTabBar`, implement a `BranchDropdown` composable in the chat header area:

```kotlin
@Composable
private fun BranchIndicator(
    branches: List<Branch>,
    activeBranch: Branch?,
    onSwitchBranch: (BranchId) -> Unit,
    onDeleteBranch: (BranchId) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = !expanded }) {
            Icon(Icons.Default.AccountTree, contentDescription = null)
            Text(
                text = activeBranch?.name ?: "No branch",
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelMedium,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            for (branch in branches) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (branch.isMain) "● " else "├─ ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(text = branch.name)
                            if (branch.id == activeBranch?.id) {
                                Text(
                                    text = " (active)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    onClick = {
                        onSwitchBranch(branch.id)
                        expanded = false
                    },
                    trailingIcon = {
                        if (!branch.isMain) {
                            IconButton(onClick = { onDeleteBranch(branch.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "Delete")
                            }
                        }
                    },
                )
            }
        }
    }
}
```

Place `BranchIndicator` in the chat header Row (in `RootContent.kt` or inline in `ChatContent.kt` top area), before the Spacer.

- [ ] **Step 5: Build to verify**

Run: `./gradlew :modules:presentation:compose-ui:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add modules/presentation/compose-ui/
git commit -m "feat(ui): implement Branch Indicator + Dropdown navigation (Variant B)"
```

---

## Task 10C: UI Variant C — Branch Panel (in wt-4-ui-panel worktree)

**Working directory:** `/Users/ilya/IdeaProjects/AiChallenge/.worktrees/wt-4-ui-panel`

Same ChatStore/ChatStoreFactory/ChatComponent changes as Task 10A (Steps 1-3 are identical). Only Step 4 differs.

- [ ] **Steps 1-3: Same as Task 10A** — Apply identical ChatStore, ChatStoreFactory, and ChatComponent changes.

- [ ] **Step 4: Implement Branch Panel UI**

Create a `BranchPanel` composable similar to `SessionSettingsPanel` — a side panel that shows a tree of branches:

```kotlin
@Composable
fun BranchPanel(
    branches: List<Branch>,
    activeBranch: Branch?,
    onSwitchBranch: (BranchId) -> Unit,
    onDeleteBranch: (BranchId) -> Unit,
) {
    Surface(
        modifier = Modifier.width(260.dp).fillMaxHeight(),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Branches",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val mainBranch = branches.firstOrNull { it.isMain }
                if (mainBranch != null) {
                    item {
                        BranchTreeNode(
                            branch = mainBranch,
                            isActive = mainBranch.id == activeBranch?.id,
                            childBranches = branches.filter { !it.isMain },
                            activeBranch = activeBranch,
                            allBranches = branches,
                            onSwitchBranch = onSwitchBranch,
                            onDeleteBranch = onDeleteBranch,
                            depth = 0,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchTreeNode(
    branch: Branch,
    isActive: Boolean,
    childBranches: List<Branch>,
    activeBranch: Branch?,
    allBranches: List<Branch>,
    onSwitchBranch: (BranchId) -> Unit,
    onDeleteBranch: (BranchId) -> Unit,
    depth: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
            .clickable { onSwitchBranch(branch.id) }
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (branch.isMain) "●" else "◦",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = branch.name,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
        )
        if (!branch.isMain) {
            IconButton(onClick = { onDeleteBranch(branch.id) }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
```

Place `BranchPanel` in the same Row as the chat content, next to it (similar to how `SessionSettingsPanel` is placed in `RootContent.kt`).

- [ ] **Step 5: Build to verify**

Run: `./gradlew :modules:presentation:compose-ui:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add modules/presentation/compose-ui/
git commit -m "feat(ui): implement Branch Panel navigation (Variant C)"
```
