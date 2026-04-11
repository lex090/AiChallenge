# Flat Branching Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace tree-based branch hierarchy with flat Turn-based branching, move active branch tracking to frontend.

**Architecture:** Branch becomes an independent aggregate with `sourceTurnId: TurnId?` instead of `parentId: BranchId?`. New `TurnSequence` value object encapsulates ordered turn lists. `activeBranchId` removed from `AgentSession` and `SessionsTable`, managed as local UI state in `ChatStore.State`. `branchId` becomes explicit parameter in `ChatService.send()` and `ContextManager.prepareContext()`.

**Tech Stack:** Kotlin 2.3.20, Exposed ORM, MVIKotlin, Compose Desktop, Arrow Either, kotlin-test

---

## File Map

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `modules/core/src/main/kotlin/com/ai/challenge/core/branch/TurnSequence.kt` | Value object: ordered turn IDs + trunk operation |
| Modify | `modules/core/src/main/kotlin/com/ai/challenge/core/branch/Branch.kt` | Flat model: `sourceTurnId`, `TurnSequence`, `ensureDeletable()` |
| Modify | `modules/core/src/main/kotlin/com/ai/challenge/core/session/AgentSession.kt` | Remove `activeBranchId`, `withActiveBranch()` |
| Delete | `modules/core/src/main/kotlin/com/ai/challenge/core/chat/model/BranchName.kt` | No longer needed |
| Modify | `modules/core/src/main/kotlin/com/ai/challenge/core/chat/BranchService.kt` | Remove `switch`, `getActive`, `getActiveTurns`, `getParentMap`; add `getTurns` |
| Modify | `modules/core/src/main/kotlin/com/ai/challenge/core/chat/ChatService.kt` | Add `branchId` parameter to `send()` |
| Modify | `modules/core/src/main/kotlin/com/ai/challenge/core/chat/AgentSessionRepository.kt` | Remove `getTurns(sessionId, limit)` |
| Modify | `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManager.kt` | Add `branchId` parameter to `prepareContext()` |
| Modify | `modules/data/session-repository-exposed/src/main/kotlin/com/ai/challenge/session/repository/SessionsTable.kt` | Remove `activeBranchId` column |
| Modify | `modules/data/session-repository-exposed/src/main/kotlin/com/ai/challenge/session/repository/BranchesTable.kt` | Replace `parentId`+`name` with `sourceTurnId` |
| Modify | `modules/data/session-repository-exposed/src/main/kotlin/com/ai/challenge/session/repository/ExposedAgentSessionRepository.kt` | Update all mappings |
| Modify | `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiBranchService.kt` | Flat create/delete, remove cascade/switch/getActive |
| Modify | `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiChatService.kt` | Accept `branchId`, remove conditional branch logic |
| Modify | `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiSessionService.kt` | Remove `activeBranchId` from create, simplify `updateContextManagementType` |
| Modify | `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/BranchingContextManager.kt` | Accept `branchId` directly |
| Modify | `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt` | Pass `branchId` through |
| Modify | `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt` | `activeBranchId` in State, flat intents |
| Modify | `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt` | New executor logic, simplified msgs |
| Modify | `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatComponent.kt` | Remove `name` from `onCreateBranch` |
| Modify | `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt` | Flat branch list, remove tree rendering, remove dialog |
| Modify | `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt` | Update DI wiring |
| Modify | `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/TestFakes.kt` | Update fakes |
| Modify | `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/BranchingContextManagerTest.kt` | Update for new signature |
| Modify | `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt` | Update for new signature |
| Modify | `modules/data/session-repository-exposed/src/test/kotlin/com/ai/challenge/session/repository/ExposedAgentSessionRepositoryTest.kt` | Update for schema changes |
| Modify | `modules/presentation/compose-ui/src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt` | Update FakeServices and tests |

---

### Task 1: Create TurnSequence value object with tests

**Files:**
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/branch/TurnSequence.kt`
- Create: `modules/core/src/test/kotlin/com/ai/challenge/core/branch/TurnSequenceTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// modules/core/src/test/kotlin/com/ai/challenge/core/branch/TurnSequenceTest.kt
package com.ai.challenge.core.branch

import com.ai.challenge.core.turn.TurnId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TurnSequenceTest {

    @Test
    fun `trunkUpTo returns sequence up to and including the given turn`() {
        val t1 = TurnId(value = "t1")
        val t2 = TurnId(value = "t2")
        val t3 = TurnId(value = "t3")
        val seq = TurnSequence(values = listOf(t1, t2, t3))

        val trunk = seq.trunkUpTo(turnId = t2)

        assertEquals(expected = listOf(t1, t2), actual = trunk.values)
    }

    @Test
    fun `trunkUpTo with first turn returns single element`() {
        val t1 = TurnId(value = "t1")
        val t2 = TurnId(value = "t2")
        val seq = TurnSequence(values = listOf(t1, t2))

        val trunk = seq.trunkUpTo(turnId = t1)

        assertEquals(expected = listOf(t1), actual = trunk.values)
    }

    @Test
    fun `trunkUpTo with last turn returns full sequence`() {
        val t1 = TurnId(value = "t1")
        val t2 = TurnId(value = "t2")
        val seq = TurnSequence(values = listOf(t1, t2))

        val trunk = seq.trunkUpTo(turnId = t2)

        assertEquals(expected = listOf(t1, t2), actual = trunk.values)
    }

    @Test
    fun `trunkUpTo throws for unknown turn`() {
        val t1 = TurnId(value = "t1")
        val seq = TurnSequence(values = listOf(t1))

        assertFailsWith<IllegalArgumentException> {
            seq.trunkUpTo(turnId = TurnId(value = "unknown"))
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :modules:core:test --tests "com.ai.challenge.core.branch.TurnSequenceTest" -q`
Expected: FAIL — `TurnSequence` class does not exist

- [ ] **Step 3: Write minimal implementation**

```kotlin
// modules/core/src/main/kotlin/com/ai/challenge/core/branch/TurnSequence.kt
package com.ai.challenge.core.branch

import com.ai.challenge.core.turn.TurnId

@JvmInline
value class TurnSequence(val values: List<TurnId>) {
    fun trunkUpTo(turnId: TurnId): TurnSequence {
        val index = values.indexOf(element = turnId)
        require(index >= 0) { "Turn ${turnId.value} not found in sequence" }
        return TurnSequence(values = values.subList(fromIndex = 0, toIndex = index + 1))
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :modules:core:test --tests "com.ai.challenge.core.branch.TurnSequenceTest" -q`
Expected: PASS — all 4 tests green

- [ ] **Step 5: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/branch/TurnSequence.kt modules/core/src/test/kotlin/com/ai/challenge/core/branch/TurnSequenceTest.kt
git commit -m "feat(core): add TurnSequence value object with trunk operation"
```

---

### Task 2: Update Branch model to flat structure

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/branch/Branch.kt`
- Delete: `modules/core/src/main/kotlin/com/ai/challenge/core/chat/model/BranchName.kt`

- [ ] **Step 1: Replace Branch.kt with flat model**

Replace the entire content of `modules/core/src/main/kotlin/com/ai/challenge/core/branch/Branch.kt` with:

```kotlin
package com.ai.challenge.core.branch

import arrow.core.Either
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.turn.TurnId

/**
 * Aggregate Root — represents a conversation branch within a session.
 *
 * [sourceTurnId] — the Turn from which this branch diverges. null means main branch.
 * [turnSequence] — ordered sequence of turn references in this branch.
 *
 * Invariants:
 * - Main branch (sourceTurnId == null) cannot be deleted
 */
data class Branch(
    val id: BranchId,
    val sessionId: AgentSessionId,
    val sourceTurnId: TurnId?,
    val turnSequence: TurnSequence,
    val createdAt: CreatedAt,
) {
    val isMain: Boolean get() = sourceTurnId == null

    fun ensureDeletable(): Either<DomainError, Unit> =
        if (isMain) Either.Left(value = DomainError.MainBranchCannotBeDeleted(sessionId = sessionId))
        else Either.Right(value = Unit)
}
```

- [ ] **Step 2: Delete BranchName.kt**

Delete file: `modules/core/src/main/kotlin/com/ai/challenge/core/chat/model/BranchName.kt`

- [ ] **Step 3: Verify core module compiles (will fail — dependents need updating, that's expected)**

Run: `./gradlew :modules:core:compileKotlin -q 2>&1 | head -5`
Expected: Compilation succeeds for core module (BranchName import errors will be in other modules, not core — since BranchName is defined in core but only imported by others). Actually, check — if anything in core imports BranchName, it will fail. Verify no core files import BranchName besides the deleted file itself.

Run: `grep -r "BranchName" modules/core/src/main/`
Expected: No results (BranchName.kt is deleted, and Branch.kt no longer imports it)

- [ ] **Step 4: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/branch/Branch.kt
git rm modules/core/src/main/kotlin/com/ai/challenge/core/chat/model/BranchName.kt
git commit -m "refactor(core): flatten Branch model — sourceTurnId replaces parentId, remove BranchName"
```

---

### Task 3: Update AgentSession — remove activeBranchId

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/session/AgentSession.kt`

- [ ] **Step 1: Replace AgentSession.kt**

Replace the entire content of `modules/core/src/main/kotlin/com/ai/challenge/core/session/AgentSession.kt` with:

```kotlin
package com.ai.challenge.core.session

import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.shared.UpdatedAt
import kotlin.time.Clock

/**
 * Aggregate Root — session-level data only.
 *
 * Does not track active branch — that is frontend state.
 */
data class AgentSession(
    val id: AgentSessionId,
    val title: SessionTitle,
    val contextManagementType: ContextManagementType,
    val createdAt: CreatedAt,
    val updatedAt: UpdatedAt,
) {
    fun withUpdatedTitle(newTitle: SessionTitle): AgentSession =
        copy(title = newTitle, updatedAt = UpdatedAt(value = Clock.System.now()))

    fun withContextManagementType(type: ContextManagementType): AgentSession =
        copy(contextManagementType = type, updatedAt = UpdatedAt(value = Clock.System.now()))
}
```

- [ ] **Step 2: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/session/AgentSession.kt
git commit -m "refactor(core): remove activeBranchId from AgentSession"
```

---

### Task 4: Update core interfaces — BranchService, ChatService, ContextManager, Repository

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/chat/BranchService.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/chat/ChatService.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/chat/AgentSessionRepository.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManager.kt`

- [ ] **Step 1: Replace BranchService.kt**

Replace the entire content of `modules/core/src/main/kotlin/com/ai/challenge/core/chat/BranchService.kt` with:

```kotlin
package com.ai.challenge.core.chat

import arrow.core.Either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId

interface BranchService {
    suspend fun create(
        sessionId: AgentSessionId,
        sourceTurnId: TurnId,
        fromBranchId: BranchId,
    ): Either<DomainError, Branch>

    suspend fun delete(branchId: BranchId): Either<DomainError, Unit>

    suspend fun getAll(sessionId: AgentSessionId): Either<DomainError, List<Branch>>

    suspend fun getTurns(branchId: BranchId): Either<DomainError, List<Turn>>
}
```

- [ ] **Step 2: Replace ChatService.kt**

Replace the entire content of `modules/core/src/main/kotlin/com/ai/challenge/core/chat/ChatService.kt` with:

```kotlin
package com.ai.challenge.core.chat

import arrow.core.Either
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn

interface ChatService {
    suspend fun send(
        sessionId: AgentSessionId,
        branchId: BranchId,
        message: MessageContent,
    ): Either<DomainError, Turn>
}
```

- [ ] **Step 3: Update AgentSessionRepository.kt — remove getTurns(sessionId, limit)**

In `modules/core/src/main/kotlin/com/ai/challenge/core/chat/AgentSessionRepository.kt`, remove the `getTurns` method and the unused `Int?` import. Replace entire file with:

```kotlin
package com.ai.challenge.core.chat

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId

interface AgentSessionRepository {
    // === Session Lifecycle ===
    suspend fun save(session: AgentSession): AgentSession
    suspend fun get(id: AgentSessionId): AgentSession?
    suspend fun delete(id: AgentSessionId)
    suspend fun list(): List<AgentSession>
    suspend fun update(session: AgentSession): AgentSession

    // === Branches ===
    suspend fun createBranch(branch: Branch): Branch
    suspend fun getBranches(sessionId: AgentSessionId): List<Branch>
    suspend fun getBranch(branchId: BranchId): Branch?
    suspend fun getMainBranch(sessionId: AgentSessionId): Branch?
    suspend fun deleteBranch(branchId: BranchId)
    suspend fun deleteTurnsByBranch(branchId: BranchId)

    // === Turns ===
    suspend fun appendTurn(branchId: BranchId, turn: Turn): Turn
    suspend fun getTurnsByBranch(branchId: BranchId): List<Turn>
    suspend fun getTurn(turnId: TurnId): Turn?
}
```

- [ ] **Step 4: Replace ContextManager.kt**

Replace the entire content of `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManager.kt` with:

```kotlin
package com.ai.challenge.core.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.session.AgentSessionId

interface ContextManager {
    suspend fun prepareContext(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
    ): PreparedContext
}
```

- [ ] **Step 5: Verify core compiles**

Run: `./gradlew :modules:core:compileKotlin -q`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/chat/BranchService.kt modules/core/src/main/kotlin/com/ai/challenge/core/chat/ChatService.kt modules/core/src/main/kotlin/com/ai/challenge/core/chat/AgentSessionRepository.kt modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManager.kt
git commit -m "refactor(core): update service interfaces for flat branching — branchId explicit, remove active/parent methods"
```

---

### Task 5: Update DB schema — SessionsTable and BranchesTable

**Files:**
- Modify: `modules/data/session-repository-exposed/src/main/kotlin/com/ai/challenge/session/repository/SessionsTable.kt`
- Modify: `modules/data/session-repository-exposed/src/main/kotlin/com/ai/challenge/session/repository/BranchesTable.kt`

- [ ] **Step 1: Replace SessionsTable.kt — remove activeBranchId column**

Replace the entire content of `modules/data/session-repository-exposed/src/main/kotlin/com/ai/challenge/session/repository/SessionsTable.kt` with:

```kotlin
package com.ai.challenge.session.repository

import org.jetbrains.exposed.sql.Table

object SessionsTable : Table("sessions") {
    val id = varchar("id", 36)
    val title = varchar("title", 255)
    val contextManagementType = varchar("context_management_type", 50)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}
```

- [ ] **Step 2: Replace BranchesTable.kt — sourceTurnId instead of parentId+name**

Replace the entire content of `modules/data/session-repository-exposed/src/main/kotlin/com/ai/challenge/session/repository/BranchesTable.kt` with:

```kotlin
package com.ai.challenge.session.repository

import org.jetbrains.exposed.sql.Table

object BranchesTable : Table("branches") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val sourceTurnId = varchar("source_turn_id", 36).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
```

- [ ] **Step 3: Commit**

```bash
git add modules/data/session-repository-exposed/src/main/kotlin/com/ai/challenge/session/repository/SessionsTable.kt modules/data/session-repository-exposed/src/main/kotlin/com/ai/challenge/session/repository/BranchesTable.kt
git commit -m "refactor(data): update DB schema — remove activeBranchId, replace parentId+name with sourceTurnId"
```

---

### Task 6: Update ExposedAgentSessionRepository

**Files:**
- Modify: `modules/data/session-repository-exposed/src/main/kotlin/com/ai/challenge/session/repository/ExposedAgentSessionRepository.kt`

- [ ] **Step 1: Replace ExposedAgentSessionRepository.kt**

Replace the entire content of `modules/data/session-repository-exposed/src/main/kotlin/com/ai/challenge/session/repository/ExposedAgentSessionRepository.kt` with:

```kotlin
package com.ai.challenge.session.repository

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.TurnSequence
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.shared.UpdatedAt
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.model.Cost
import com.ai.challenge.core.usage.model.TokenCount
import com.ai.challenge.core.usage.model.UsageRecord
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import kotlin.time.Instant

class ExposedAgentSessionRepository(
    private val database: Database,
) : AgentSessionRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(
                SessionsTable,
                TurnsTable,
                BranchesTable,
                BranchTurnsTable,
            )
        }
    }

    // === Session Lifecycle ===

    override suspend fun save(session: AgentSession): AgentSession {
        transaction(db = database) {
            SessionsTable.insert {
                it[id] = session.id.value
                it[title] = session.title.value
                it[contextManagementType] = session.contextManagementType.toStorageString()
                it[createdAt] = session.createdAt.value.toEpochMilliseconds()
                it[updatedAt] = session.updatedAt.value.toEpochMilliseconds()
            }
        }
        return session
    }

    override suspend fun get(id: AgentSessionId): AgentSession? = transaction(database) {
        SessionsTable.selectAll()
            .where { SessionsTable.id eq id.value }
            .singleOrNull()
            ?.toAgentSession()
    }

    override suspend fun delete(id: AgentSessionId) {
        transaction(database) {
            val branchIds = BranchesTable.selectAll()
                .where { BranchesTable.sessionId eq id.value }
                .map { it[BranchesTable.id] }
            for (branchId in branchIds) {
                BranchTurnsTable.deleteWhere { BranchTurnsTable.branchId eq branchId }
            }
            BranchesTable.deleteWhere { BranchesTable.sessionId eq id.value }
            TurnsTable.deleteWhere { TurnsTable.sessionId eq id.value }
            SessionsTable.deleteWhere { SessionsTable.id eq id.value }
        }
    }

    override suspend fun list(): List<AgentSession> = transaction(database) {
        SessionsTable.selectAll()
            .orderBy(SessionsTable.updatedAt, SortOrder.DESC)
            .map { it.toAgentSession() }
    }

    override suspend fun update(session: AgentSession): AgentSession {
        transaction(db = database) {
            SessionsTable.update(where = { SessionsTable.id eq session.id.value }) {
                it[title] = session.title.value
                it[contextManagementType] = session.contextManagementType.toStorageString()
                it[updatedAt] = session.updatedAt.value.toEpochMilliseconds()
            }
        }
        return session
    }

    // === Branches ===

    override suspend fun createBranch(branch: Branch): Branch {
        transaction(database) {
            BranchesTable.insert {
                it[id] = branch.id.value
                it[sessionId] = branch.sessionId.value
                it[sourceTurnId] = branch.sourceTurnId?.value
                it[createdAt] = branch.createdAt.value.toEpochMilliseconds()
            }
            for ((index, turnId) in branch.turnSequence.values.withIndex()) {
                BranchTurnsTable.insert {
                    it[branchId] = branch.id.value
                    it[BranchTurnsTable.turnId] = turnId.value
                    it[orderIndex] = index
                }
            }
        }
        return branch
    }

    override suspend fun getBranches(sessionId: AgentSessionId): List<Branch> = transaction(database) {
        BranchesTable.selectAll()
            .where { BranchesTable.sessionId eq sessionId.value }
            .map { it.toBranch() }
    }

    override suspend fun getBranch(branchId: BranchId): Branch? = transaction(database) {
        BranchesTable.selectAll()
            .where { BranchesTable.id eq branchId.value }
            .singleOrNull()
            ?.toBranch()
    }

    override suspend fun getMainBranch(sessionId: AgentSessionId): Branch? = transaction(database) {
        BranchesTable.selectAll()
            .where { (BranchesTable.sessionId eq sessionId.value) and BranchesTable.sourceTurnId.isNull() }
            .singleOrNull()
            ?.toBranch()
    }

    override suspend fun deleteBranch(branchId: BranchId) {
        transaction(database) {
            BranchTurnsTable.deleteWhere { BranchTurnsTable.branchId eq branchId.value }
            BranchesTable.deleteWhere { BranchesTable.id eq branchId.value }
        }
    }

    override suspend fun deleteTurnsByBranch(branchId: BranchId) {
        transaction(database) {
            BranchTurnsTable.deleteWhere { BranchTurnsTable.branchId eq branchId.value }
        }
    }

    // === Turns ===

    override suspend fun appendTurn(branchId: BranchId, turn: Turn): Turn {
        transaction(database) {
            TurnsTable.insert {
                it[id] = turn.id.value
                it[sessionId] = turn.sessionId.value
                it[userMessage] = turn.userMessage.value
                it[assistantMessage] = turn.assistantMessage.value
                it[createdAt] = turn.createdAt.value.toEpochMilliseconds()
                it[promptTokens] = turn.usage.promptTokens.value
                it[completionTokens] = turn.usage.completionTokens.value
                it[cachedTokens] = turn.usage.cachedTokens.value
                it[cacheWriteTokens] = turn.usage.cacheWriteTokens.value
                it[reasoningTokens] = turn.usage.reasoningTokens.value
                it[totalCost] = turn.usage.totalCost.value.toString()
                it[upstreamCost] = turn.usage.upstreamCost.value.toString()
                it[upstreamPromptCost] = turn.usage.upstreamPromptCost.value.toString()
                it[upstreamCompletionsCost] = turn.usage.upstreamCompletionsCost.value.toString()
            }
            val maxIndex = BranchTurnsTable.selectAll()
                .where { BranchTurnsTable.branchId eq branchId.value }
                .maxByOrNull { it[BranchTurnsTable.orderIndex] }
                ?.get(BranchTurnsTable.orderIndex)
            BranchTurnsTable.insert {
                it[BranchTurnsTable.branchId] = branchId.value
                it[turnId] = turn.id.value
                it[orderIndex] = (maxIndex ?: -1) + 1
            }
        }
        return turn
    }

    override suspend fun getTurnsByBranch(branchId: BranchId): List<Turn> = transaction(database) {
        val turnIds = BranchTurnsTable.selectAll()
            .where { BranchTurnsTable.branchId eq branchId.value }
            .orderBy(BranchTurnsTable.orderIndex, SortOrder.ASC)
            .map { it[BranchTurnsTable.turnId] }

        turnIds.mapNotNull { turnId ->
            TurnsTable.selectAll()
                .where { TurnsTable.id eq turnId }
                .singleOrNull()
                ?.toTurn()
        }
    }

    override suspend fun getTurn(turnId: TurnId): Turn? = transaction(database) {
        TurnsTable.selectAll()
            .where { TurnsTable.id eq turnId.value }
            .singleOrNull()
            ?.toTurn()
    }

    // === Mapping ===

    private fun ResultRow.toAgentSession() = AgentSession(
        id = AgentSessionId(value = this[SessionsTable.id]),
        title = SessionTitle(value = this[SessionsTable.title]),
        contextManagementType = this[SessionsTable.contextManagementType].toContextManagementType(),
        createdAt = CreatedAt(value = Instant.fromEpochMilliseconds(this[SessionsTable.createdAt])),
        updatedAt = UpdatedAt(value = Instant.fromEpochMilliseconds(this[SessionsTable.updatedAt])),
    )

    private fun ResultRow.toBranch(): Branch {
        val branchIdValue = this[BranchesTable.id]
        val turnIds = BranchTurnsTable.selectAll()
            .where { BranchTurnsTable.branchId eq branchIdValue }
            .orderBy(BranchTurnsTable.orderIndex, SortOrder.ASC)
            .map { TurnId(value = it[BranchTurnsTable.turnId]) }
        return Branch(
            id = BranchId(value = branchIdValue),
            sessionId = AgentSessionId(value = this[BranchesTable.sessionId]),
            sourceTurnId = this[BranchesTable.sourceTurnId]?.let { TurnId(value = it) },
            turnSequence = TurnSequence(values = turnIds),
            createdAt = CreatedAt(value = Instant.fromEpochMilliseconds(this[BranchesTable.createdAt])),
        )
    }

    private fun ResultRow.toTurn() = Turn(
        id = TurnId(value = this[TurnsTable.id]),
        sessionId = AgentSessionId(value = this[TurnsTable.sessionId]),
        userMessage = MessageContent(value = this[TurnsTable.userMessage]),
        assistantMessage = MessageContent(value = this[TurnsTable.assistantMessage]),
        usage = UsageRecord(
            promptTokens = TokenCount(value = this[TurnsTable.promptTokens]),
            completionTokens = TokenCount(value = this[TurnsTable.completionTokens]),
            cachedTokens = TokenCount(value = this[TurnsTable.cachedTokens]),
            cacheWriteTokens = TokenCount(value = this[TurnsTable.cacheWriteTokens]),
            reasoningTokens = TokenCount(value = this[TurnsTable.reasoningTokens]),
            totalCost = Cost(value = BigDecimal(this[TurnsTable.totalCost])),
            upstreamCost = Cost(value = BigDecimal(this[TurnsTable.upstreamCost])),
            upstreamPromptCost = Cost(value = BigDecimal(this[TurnsTable.upstreamPromptCost])),
            upstreamCompletionsCost = Cost(value = BigDecimal(this[TurnsTable.upstreamCompletionsCost])),
        ),
        createdAt = CreatedAt(value = Instant.fromEpochMilliseconds(this[TurnsTable.createdAt])),
    )
}

private fun ContextManagementType.toStorageString(): String = when (this) {
    is ContextManagementType.None -> "none"
    is ContextManagementType.SummarizeOnThreshold -> "summarize_on_threshold"
    is ContextManagementType.SlidingWindow -> "sliding_window"
    is ContextManagementType.StickyFacts -> "sticky_facts"
    is ContextManagementType.Branching -> "branching"
}

private fun String.toContextManagementType(): ContextManagementType = when (this) {
    "none" -> ContextManagementType.None
    "summarize_on_threshold" -> ContextManagementType.SummarizeOnThreshold
    "sliding_window" -> ContextManagementType.SlidingWindow
    "sticky_facts" -> ContextManagementType.StickyFacts
    "branching" -> ContextManagementType.Branching
    else -> ContextManagementType.None
}
```

- [ ] **Step 2: Commit**

```bash
git add modules/data/session-repository-exposed/src/main/kotlin/com/ai/challenge/session/repository/ExposedAgentSessionRepository.kt
git commit -m "refactor(data): update ExposedAgentSessionRepository for flat branching"
```

---

### Task 7: Update domain services — AiBranchService, AiChatService, AiSessionService

**Files:**
- Modify: `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiBranchService.kt`
- Modify: `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiChatService.kt`
- Modify: `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiSessionService.kt`

- [ ] **Step 1: Replace AiBranchService.kt**

Replace the entire content of `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiBranchService.kt` with:

```kotlin
package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.TurnSequence
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.BranchService
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import kotlin.time.Clock

class AiBranchService(
    private val repository: AgentSessionRepository,
) : BranchService {

    override suspend fun create(
        sessionId: AgentSessionId,
        sourceTurnId: TurnId,
        fromBranchId: BranchId,
    ): Either<DomainError, Branch> = either {
        val session = repository.get(id = sessionId)
            ?: raise(DomainError.SessionNotFound(id = sessionId))

        if (session.contextManagementType !is ContextManagementType.Branching) {
            raise(DomainError.BranchingNotEnabled(sessionId = sessionId))
        }

        val fromBranch = repository.getBranch(branchId = fromBranchId)
            ?: raise(DomainError.BranchNotFound(id = fromBranchId))

        val trunkSequence = fromBranch.turnSequence.trunkUpTo(turnId = sourceTurnId)

        val branch = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            sourceTurnId = sourceTurnId,
            turnSequence = trunkSequence,
            createdAt = CreatedAt(value = Clock.System.now()),
        )
        repository.createBranch(branch = branch)
    }

    override suspend fun delete(branchId: BranchId): Either<DomainError, Unit> = either {
        val branch = repository.getBranch(branchId = branchId)
            ?: raise(DomainError.BranchNotFound(id = branchId))

        branch.ensureDeletable().bind()

        repository.deleteTurnsByBranch(branchId = branchId)
        repository.deleteBranch(branchId = branchId)
    }

    override suspend fun getAll(sessionId: AgentSessionId): Either<DomainError, List<Branch>> =
        Either.Right(value = repository.getBranches(sessionId = sessionId))

    override suspend fun getTurns(branchId: BranchId): Either<DomainError, List<Turn>> = either {
        repository.getBranch(branchId = branchId)
            ?: raise(DomainError.BranchNotFound(id = branchId))
        repository.getTurnsByBranch(branchId = branchId)
    }
}
```

- [ ] **Step 2: Replace AiChatService.kt**

Replace the entire content of `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiChatService.kt` with:

```kotlin
package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.ChatService
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.model.Cost
import com.ai.challenge.core.usage.model.TokenCount
import com.ai.challenge.core.usage.model.UsageRecord
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.llm.model.ChatResponse
import java.math.BigDecimal
import kotlin.time.Clock

class AiChatService(
    private val service: OpenRouterService,
    private val model: String,
    private val repository: AgentSessionRepository,
    private val contextManager: ContextManager,
) : ChatService {

    override suspend fun send(
        sessionId: AgentSessionId,
        branchId: BranchId,
        message: MessageContent,
    ): Either<DomainError, Turn> = either {
        repository.get(id = sessionId)
            ?: raise(DomainError.SessionNotFound(id = sessionId))

        val context = catch({
            contextManager.prepareContext(sessionId = sessionId, branchId = branchId, newMessage = message)
        }) { e: Exception ->
            raise(DomainError.NetworkError(message = e.message ?: "Context preparation failed"))
        }

        val chatResponse = catch({
            service.chat(model = model) {
                for (msg in context.messages) {
                    message(role = msg.role.toApiRole(), content = msg.content.value)
                }
            }
        }) { e: Exception ->
            val msg = e.message ?: "Unknown error"
            if (msg.startsWith("OpenRouter API error:")) {
                raise(DomainError.ApiError(message = msg.removePrefix("OpenRouter API error: ")))
            } else {
                raise(DomainError.NetworkError(message = msg))
            }
        }

        val error = chatResponse.error
        if (error != null) {
            raise(DomainError.ApiError(message = error.message ?: "Unknown API error"))
        }

        val text = chatResponse.choices.firstOrNull()?.message?.content
            ?: raise(DomainError.ApiError(message = "Empty response from OpenRouter"))

        val usage = chatResponse.toUsageRecord()
        val turn = Turn(
            id = TurnId.generate(),
            sessionId = sessionId,
            userMessage = message,
            assistantMessage = MessageContent(value = text),
            usage = usage,
            createdAt = CreatedAt(value = Clock.System.now()),
        )

        repository.appendTurn(branchId = branchId, turn = turn)
    }
}

fun MessageRole.toApiRole(): String = when (this) {
    MessageRole.System -> "system"
    MessageRole.User -> "user"
    MessageRole.Assistant -> "assistant"
}

private fun ChatResponse.toUsageRecord(): UsageRecord = UsageRecord(
    promptTokens = TokenCount(value = usage?.promptTokens ?: 0),
    completionTokens = TokenCount(value = usage?.completionTokens ?: 0),
    cachedTokens = TokenCount(value = usage?.promptTokensDetails?.cachedTokens ?: 0),
    cacheWriteTokens = TokenCount(value = usage?.promptTokensDetails?.cacheWriteTokens ?: 0),
    reasoningTokens = TokenCount(value = usage?.completionTokensDetails?.reasoningTokens ?: 0),
    totalCost = Cost(value = BigDecimal.valueOf(usage?.cost ?: cost ?: 0.0)),
    upstreamCost = Cost(value = BigDecimal.valueOf(usage?.costDetails?.upstreamCost ?: costDetails?.upstreamCost ?: 0.0)),
    upstreamPromptCost = Cost(value = BigDecimal.valueOf(usage?.costDetails?.upstreamPromptCost ?: costDetails?.upstreamPromptCost ?: 0.0)),
    upstreamCompletionsCost = Cost(value = BigDecimal.valueOf(usage?.costDetails?.upstreamCompletionsCost ?: costDetails?.upstreamCompletionsCost ?: 0.0)),
)
```

- [ ] **Step 3: Replace AiSessionService.kt**

Replace the entire content of `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiSessionService.kt` with:

```kotlin
package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.TurnSequence
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.shared.UpdatedAt
import kotlin.time.Clock

class AiSessionService(
    private val repository: AgentSessionRepository,
) : SessionService {

    override suspend fun create(title: SessionTitle): Either<DomainError, AgentSession> = either {
        val now = Clock.System.now()
        val session = AgentSession(
            id = AgentSessionId.generate(),
            title = title,
            contextManagementType = ContextManagementType.None,
            createdAt = CreatedAt(value = now),
            updatedAt = UpdatedAt(value = now),
        )
        val savedSession = repository.save(session = session)

        val mainBranch = Branch(
            id = BranchId.generate(),
            sessionId = savedSession.id,
            sourceTurnId = null,
            turnSequence = TurnSequence(values = emptyList()),
            createdAt = CreatedAt(value = now),
        )
        repository.createBranch(branch = mainBranch)

        savedSession
    }

    override suspend fun get(id: AgentSessionId): Either<DomainError, AgentSession> = either {
        repository.get(id = id)
            ?: raise(DomainError.SessionNotFound(id = id))
    }

    override suspend fun delete(id: AgentSessionId): Either<DomainError, Unit> = either {
        repository.get(id = id)
            ?: raise(DomainError.SessionNotFound(id = id))
        repository.delete(id = id)
    }

    override suspend fun list(): Either<DomainError, List<AgentSession>> =
        Either.Right(value = repository.list())

    override suspend fun updateTitle(
        id: AgentSessionId,
        title: SessionTitle,
    ): Either<DomainError, AgentSession> = either {
        val session = repository.get(id = id)
            ?: raise(DomainError.SessionNotFound(id = id))
        repository.update(session = session.withUpdatedTitle(newTitle = title))
    }

    override suspend fun updateContextManagementType(
        id: AgentSessionId,
        type: ContextManagementType,
    ): Either<DomainError, AgentSession> = either {
        val session = repository.get(id = id)
            ?: raise(DomainError.SessionNotFound(id = id))

        val updatedSession = repository.update(session = session.withContextManagementType(type = type))

        if (type is ContextManagementType.Branching) {
            val existingMain = repository.getMainBranch(sessionId = id)
            if (existingMain == null) {
                val now = Clock.System.now()
                val mainBranch = Branch(
                    id = BranchId.generate(),
                    sessionId = id,
                    sourceTurnId = null,
                    turnSequence = TurnSequence(values = emptyList()),
                    createdAt = CreatedAt(value = now),
                )
                repository.createBranch(branch = mainBranch)
            }
        }

        updatedSession
    }
}
```

Note: `AiSessionService` no longer takes `contextManager` as a dependency — it was only used for the removed migration logic. The `updateContextManagementType` no longer migrates existing turns to main branch — those turns are already in the main branch since all turns are always appended to a branch.

- [ ] **Step 4: Commit**

```bash
git add modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiBranchService.kt modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiChatService.kt modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiSessionService.kt
git commit -m "refactor(domain): update domain services for flat branching — simplify branch/chat/session"
```

---

### Task 8: Update context managers — BranchingContextManager and DefaultContextManager

**Files:**
- Modify: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/BranchingContextManager.kt`
- Modify: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt`

- [ ] **Step 1: Replace BranchingContextManager.kt**

Replace the entire content with:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.context.PreparedContext
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn

class BranchingContextManager(
    private val repository: AgentSessionRepository,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
    ): PreparedContext {
        val turns = repository.getTurnsByBranch(branchId = branchId)
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

    private fun turnsToMessages(turns: List<Turn>): List<ContextMessage> =
        turns.flatMap {
            listOf(
                ContextMessage(role = MessageRole.User, content = it.userMessage),
                ContextMessage(role = MessageRole.Assistant, content = it.assistantMessage),
            )
        }
}
```

- [ ] **Step 2: Update DefaultContextManager.kt**

In `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt`, the `prepareContext` signature needs to accept `branchId`, and the non-branching strategies need to get turns from the main branch instead of `getTurns(sessionId, limit)`. Replace the entire file with:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.context.PreparedContext
import com.ai.challenge.core.context.model.SummaryContent
import com.ai.challenge.core.context.model.TurnIndex
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.turn.Turn
import kotlin.time.Clock

class DefaultContextManager(
    private val repository: AgentSessionRepository,
    private val compressor: ContextCompressor,
    private val summaryRepository: SummaryRepository,
    private val factExtractor: FactExtractor,
    private val factRepository: FactRepository,
    private val branchingContextManager: BranchingContextManager,
) : ContextManager {

    private companion object {
        const val WINDOW_SIZE = 10
    }

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
    ): PreparedContext {
        val session = repository.get(id = sessionId)
            ?: error("Session not found: ${sessionId.value}")
        val type = session.contextManagementType

        return when (type) {
            is ContextManagementType.None -> passThrough(branchId = branchId, newMessage = newMessage)
            is ContextManagementType.SummarizeOnThreshold -> summarizeOnThreshold(
                sessionId = sessionId,
                branchId = branchId,
                newMessage = newMessage,
            )
            is ContextManagementType.Branching -> branchingContextManager.prepareContext(
                sessionId = sessionId,
                branchId = branchId,
                newMessage = newMessage,
            )
            is ContextManagementType.SlidingWindow -> slidingWindow(
                branchId = branchId,
                newMessage = newMessage,
            )
            is ContextManagementType.StickyFacts -> stickyFacts(
                sessionId = sessionId,
                branchId = branchId,
                newMessage = newMessage,
            )
        }
    }

    // --- orchestration (side effects at boundaries) ---

    private suspend fun passThrough(
        branchId: BranchId,
        newMessage: MessageContent,
    ): PreparedContext {
        val history = repository.getTurnsByBranch(branchId = branchId)
        return withoutCompression(history = history, newMessage = newMessage)
    }

    private suspend fun summarizeOnThreshold(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
    ): PreparedContext {
        val maxTurns = 15
        val retainLast = 5
        val compressionInterval = 10

        val history = repository.getTurnsByBranch(branchId = branchId)

        if (history.size < maxTurns) {
            return withoutCompression(history = history, newMessage = newMessage)
        }

        val lastSummary = summaryRepository.getBySession(sessionId = sessionId).maxByOrNull { it.toTurnIndex.value }

        if (lastSummary != null) {
            val turnsSinceLastSummary = history.size - lastSummary.toTurnIndex.value
            if (turnsSinceLastSummary < retainLast + compressionInterval) {
                return withExistingSummary(summary = lastSummary, history = history, newMessage = newMessage)
            }
        }

        val splitAt = (history.size - retainLast).coerceAtLeast(minimumValue = 0)
        val summaryContent = compressTurns(history = history, splitAt = splitAt, lastSummary = lastSummary)
        saveSummary(sessionId = sessionId, summaryContent = summaryContent, toTurnIndex = splitAt)
        return withNewSummary(summaryContent = summaryContent, history = history, splitAt = splitAt, newMessage = newMessage)
    }

    private suspend fun slidingWindow(
        branchId: BranchId,
        newMessage: MessageContent,
    ): PreparedContext {
        val history = repository.getTurnsByBranch(branchId = branchId)
        val windowed = history.takeLast(n = WINDOW_SIZE)
        return PreparedContext(
            messages = turnsToMessages(turns = windowed) + ContextMessage(role = MessageRole.User, content = newMessage),
            compressed = false,
            originalTurnCount = history.size,
            retainedTurnCount = windowed.size,
            summaryCount = 0,
        )
    }

    private suspend fun stickyFacts(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
    ): PreparedContext {
        val retainLast = 5

        val currentFacts = factRepository.getBySession(sessionId = sessionId)
        val history = repository.getTurnsByBranch(branchId = branchId)
        val lastAssistantResponse = history.lastOrNull()?.assistantMessage

        val updatedFacts = factExtractor.extract(
            sessionId = sessionId,
            currentFacts = currentFacts,
            newUserMessage = newMessage,
            lastAssistantResponse = lastAssistantResponse,
        )
        if (updatedFacts.isEmpty()) {
            factRepository.deleteBySession(sessionId = sessionId)
        } else {
            factRepository.save(sessionId = sessionId, facts = updatedFacts)
        }

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

    private suspend fun saveSummary(
        sessionId: AgentSessionId,
        summaryContent: SummaryContent,
        toTurnIndex: Int,
    ) {
        summaryRepository.save(
            summary = Summary(
                sessionId = sessionId,
                content = summaryContent,
                fromTurnIndex = TurnIndex(value = 0),
                toTurnIndex = TurnIndex(value = toTurnIndex),
                createdAt = CreatedAt(value = Clock.System.now()),
            ),
        )
    }

    // --- pure functions ---

    private fun turnsToMessages(turns: List<Turn>): List<ContextMessage> =
        turns.flatMap {
            listOf(
                ContextMessage(role = MessageRole.User, content = it.userMessage),
                ContextMessage(role = MessageRole.Assistant, content = it.assistantMessage),
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

- [ ] **Step 3: Commit**

```bash
git add modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/BranchingContextManager.kt modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt
git commit -m "refactor(context): update context managers to accept branchId explicitly"
```

---

### Task 9: Update presentation layer — ChatStore, ChatComponent, ChatContent

**Files:**
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatComponent.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt`

- [ ] **Step 1: Replace ChatStore.kt**

```kotlin
package com.ai.challenge.ui.chat.store

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.model.Cost
import com.ai.challenge.core.usage.model.TokenCount
import com.ai.challenge.core.usage.model.UsageRecord
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store
import java.math.BigDecimal

interface ChatStore : Store<ChatStore.Intent, ChatStore.State, Nothing> {

    sealed interface Intent {
        data class SendMessage(val text: String) : Intent
        data class LoadSession(val sessionId: AgentSessionId) : Intent
        data class CreateBranch(val sourceTurnId: TurnId) : Intent
        data class SwitchBranch(val branchId: BranchId) : Intent
        data class DeleteBranch(val branchId: BranchId) : Intent
        data object LoadBranches : Intent
    }

    data class State(
        val sessionId: AgentSessionId? = null,
        val messages: List<UiMessage> = emptyList(),
        val isLoading: Boolean = false,
        val turnUsage: Map<TurnId, UsageRecord> = emptyMap(),
        val sessionUsage: UsageRecord = UsageRecord(
            promptTokens = TokenCount(value = 0),
            completionTokens = TokenCount(value = 0),
            cachedTokens = TokenCount(value = 0),
            cacheWriteTokens = TokenCount(value = 0),
            reasoningTokens = TokenCount(value = 0),
            totalCost = Cost(value = BigDecimal.ZERO),
            upstreamCost = Cost(value = BigDecimal.ZERO),
            upstreamPromptCost = Cost(value = BigDecimal.ZERO),
            upstreamCompletionsCost = Cost(value = BigDecimal.ZERO),
        ),
        val branches: List<Branch> = emptyList(),
        val activeBranchId: BranchId? = null,
        val isBranchingEnabled: Boolean = false,
    )
}
```

- [ ] **Step 2: Replace ChatStoreFactory.kt**

```kotlin
package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.BranchService
import com.ai.challenge.core.chat.ChatService
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.UsageService
import com.ai.challenge.core.usage.model.Cost
import com.ai.challenge.core.usage.model.TokenCount
import com.ai.challenge.core.usage.model.UsageRecord
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch
import java.math.BigDecimal

class ChatStoreFactory(
    private val storeFactory: StoreFactory,
    private val chatService: ChatService,
    private val sessionService: SessionService,
    private val usageService: UsageService,
    private val branchService: BranchService,
) {
    fun create(): ChatStore =
        object : ChatStore,
            Store<ChatStore.Intent, ChatStore.State, Nothing> by storeFactory.create(
                name = "ChatStore",
                initialState = ChatStore.State(),
                executorFactory = { ExecutorImpl(
                    chatService = chatService,
                    sessionService = sessionService,
                    usageService = usageService,
                    branchService = branchService,
                ) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class SessionLoaded(
            val sessionId: AgentSessionId,
            val messages: List<UiMessage>,
            val turnUsage: Map<TurnId, UsageRecord>,
            val sessionUsage: UsageRecord,
            val branches: List<Branch>,
            val activeBranchId: BranchId?,
            val isBranchingEnabled: Boolean,
        ) : Msg
        data class UserMessage(val text: String) : Msg
        data class AgentResponseMsg(
            val text: String,
            val turnId: TurnId,
            val usage: UsageRecord,
        ) : Msg
        data class Error(val text: String) : Msg
        data object Loading : Msg
        data object LoadingComplete : Msg
        data class BranchesLoaded(
            val branches: List<Branch>,
            val activeBranchId: BranchId?,
            val isBranchingEnabled: Boolean,
        ) : Msg
        data class BranchSwitched(
            val messages: List<UiMessage>,
            val activeBranchId: BranchId?,
            val branches: List<Branch>,
            val turnUsage: Map<TurnId, UsageRecord>,
            val sessionUsage: UsageRecord,
        ) : Msg
    }

    private class ExecutorImpl(
        private val chatService: ChatService,
        private val sessionService: SessionService,
        private val usageService: UsageService,
        private val branchService: BranchService,
    ) : CoroutineExecutor<ChatStore.Intent, Nothing, ChatStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: ChatStore.Intent) {
            when (intent) {
                is ChatStore.Intent.SendMessage -> handleSendMessage(text = intent.text)
                is ChatStore.Intent.LoadSession -> handleLoadSession(sessionId = intent.sessionId)
                is ChatStore.Intent.CreateBranch -> handleCreateBranch(sourceTurnId = intent.sourceTurnId)
                is ChatStore.Intent.SwitchBranch -> handleSwitchBranch(branchId = intent.branchId)
                is ChatStore.Intent.DeleteBranch -> handleDeleteBranch(branchId = intent.branchId)
                is ChatStore.Intent.LoadBranches -> handleLoadBranches()
            }
        }

        private fun handleLoadSession(sessionId: AgentSessionId) {
            scope.launch {
                val sessionResult = sessionService.get(id = sessionId)
                val isBranching = sessionResult is Either.Right && sessionResult.value.contextManagementType is ContextManagementType.Branching

                val branches = if (isBranching) {
                    when (val r = branchService.getAll(sessionId = sessionId)) {
                        is Either.Right -> r.value
                        is Either.Left -> emptyList()
                    }
                } else emptyList()

                val mainBranchId = branches.firstOrNull { it.isMain }?.id
                val activeBranchId = mainBranchId

                val history = if (activeBranchId != null) {
                    when (val r = branchService.getTurns(branchId = activeBranchId)) {
                        is Either.Right -> r.value
                        is Either.Left -> emptyList()
                    }
                } else emptyList()

                val messages = history.flatMap { turn ->
                    listOf(
                        UiMessage(text = turn.userMessage.value, isUser = true, turnId = turn.id),
                        UiMessage(text = turn.assistantMessage.value, isUser = false, turnId = turn.id),
                    )
                }
                val turnUsage = when (val r = usageService.getBySession(sessionId = sessionId)) {
                    is Either.Right -> r.value
                    is Either.Left -> emptyMap()
                }
                val sessionUsage = turnUsage.values.fold(emptyUsageRecord()) { acc, u -> acc + u }
                dispatch(Msg.SessionLoaded(
                    sessionId = sessionId,
                    messages = messages,
                    turnUsage = turnUsage,
                    sessionUsage = sessionUsage,
                    branches = branches,
                    activeBranchId = activeBranchId,
                    isBranchingEnabled = isBranching,
                ))
            }
        }

        private fun handleSendMessage(text: String) {
            val sessionId = state().sessionId ?: return
            val branchId = state().activeBranchId ?: return
            dispatch(Msg.UserMessage(text = text))
            dispatch(Msg.Loading)

            scope.launch {
                when (val result = chatService.send(sessionId = sessionId, branchId = branchId, message = MessageContent(value = text))) {
                    is Either.Right -> {
                        val turn = result.value
                        dispatch(
                            Msg.AgentResponseMsg(
                                text = turn.assistantMessage.value,
                                turnId = turn.id,
                                usage = turn.usage,
                            )
                        )
                    }
                    is Either.Left -> dispatch(Msg.Error(text = result.value.message))
                }
                dispatch(Msg.LoadingComplete)

                when (val sessionResult = sessionService.get(id = sessionId)) {
                    is Either.Right -> {
                        if (sessionResult.value.title.value.isEmpty()) {
                            sessionService.updateTitle(id = sessionId, title = SessionTitle(value = text.take(n = 50)))
                        }
                    }
                    is Either.Left -> {}
                }
            }
        }

        private fun handleLoadBranches() {
            val sessionId = state().sessionId ?: return
            scope.launch {
                val sessionResult = sessionService.get(id = sessionId)
                val isBranching = sessionResult is Either.Right && sessionResult.value.contextManagementType is ContextManagementType.Branching
                val branches = if (isBranching) {
                    when (val r = branchService.getAll(sessionId = sessionId)) {
                        is Either.Right -> r.value
                        is Either.Left -> emptyList()
                    }
                } else emptyList()
                dispatch(Msg.BranchesLoaded(
                    branches = branches,
                    activeBranchId = state().activeBranchId,
                    isBranchingEnabled = isBranching,
                ))
            }
        }

        private fun handleCreateBranch(sourceTurnId: TurnId) {
            val sessionId = state().sessionId ?: return
            val activeBranchId = state().activeBranchId ?: return
            scope.launch {
                when (val result = branchService.create(sessionId = sessionId, sourceTurnId = sourceTurnId, fromBranchId = activeBranchId)) {
                    is Either.Right -> {
                        val newBranch = result.value
                        handleSwitchBranch(branchId = newBranch.id)
                    }
                    is Either.Left -> {}
                }
            }
        }

        private fun handleSwitchBranch(branchId: BranchId) {
            val sessionId = state().sessionId ?: return
            dispatch(Msg.Loading)
            scope.launch {
                val history = when (val r = branchService.getTurns(branchId = branchId)) {
                    is Either.Right -> r.value
                    is Either.Left -> emptyList()
                }
                val messages = history.flatMap { turn ->
                    listOf(
                        UiMessage(text = turn.userMessage.value, isUser = true, turnId = turn.id),
                        UiMessage(text = turn.assistantMessage.value, isUser = false, turnId = turn.id),
                    )
                }
                val turnUsage = when (val r = usageService.getBySession(sessionId = sessionId)) {
                    is Either.Right -> r.value
                    is Either.Left -> emptyMap()
                }
                val sessionUsage = turnUsage.values.fold(emptyUsageRecord()) { acc, u -> acc + u }
                val branches = when (val r = branchService.getAll(sessionId = sessionId)) {
                    is Either.Right -> r.value
                    is Either.Left -> emptyList()
                }
                dispatch(Msg.BranchSwitched(
                    messages = messages,
                    activeBranchId = branchId,
                    branches = branches,
                    turnUsage = turnUsage,
                    sessionUsage = sessionUsage,
                ))
                dispatch(Msg.LoadingComplete)
            }
        }

        private fun handleDeleteBranch(branchId: BranchId) {
            val sessionId = state().sessionId ?: return
            scope.launch {
                when (branchService.delete(branchId = branchId)) {
                    is Either.Right -> {
                        val isActive = state().activeBranchId == branchId
                        if (isActive) {
                            val branches = when (val r = branchService.getAll(sessionId = sessionId)) {
                                is Either.Right -> r.value
                                is Either.Left -> emptyList()
                            }
                            val mainId = branches.firstOrNull { it.isMain }?.id
                            if (mainId != null) {
                                handleSwitchBranch(branchId = mainId)
                            }
                        } else {
                            handleLoadBranches()
                        }
                    }
                    is Either.Left -> {}
                }
            }
        }

        private fun emptyUsageRecord(): UsageRecord = UsageRecord(
            promptTokens = TokenCount(value = 0),
            completionTokens = TokenCount(value = 0),
            cachedTokens = TokenCount(value = 0),
            cacheWriteTokens = TokenCount(value = 0),
            reasoningTokens = TokenCount(value = 0),
            totalCost = Cost(value = BigDecimal.ZERO),
            upstreamCost = Cost(value = BigDecimal.ZERO),
            upstreamPromptCost = Cost(value = BigDecimal.ZERO),
            upstreamCompletionsCost = Cost(value = BigDecimal.ZERO),
        )
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<ChatStore.State, Msg> {
        override fun ChatStore.State.reduce(msg: Msg): ChatStore.State =
            when (msg) {
                is Msg.SessionLoaded -> copy(
                    sessionId = msg.sessionId,
                    messages = msg.messages,
                    turnUsage = msg.turnUsage,
                    sessionUsage = msg.sessionUsage,
                    branches = msg.branches,
                    activeBranchId = msg.activeBranchId,
                    isBranchingEnabled = msg.isBranchingEnabled,
                )
                is Msg.UserMessage -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = true),
                )
                is Msg.AgentResponseMsg -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false, turnId = msg.turnId),
                    turnUsage = turnUsage + (msg.turnId to msg.usage),
                    sessionUsage = sessionUsage + msg.usage,
                )
                is Msg.Error -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false, isError = true),
                )
                is Msg.Loading -> copy(isLoading = true)
                is Msg.LoadingComplete -> copy(isLoading = false)
                is Msg.BranchesLoaded -> copy(
                    branches = msg.branches,
                    activeBranchId = msg.activeBranchId,
                    isBranchingEnabled = msg.isBranchingEnabled,
                )
                is Msg.BranchSwitched -> copy(
                    messages = msg.messages,
                    activeBranchId = msg.activeBranchId,
                    branches = msg.branches,
                    turnUsage = msg.turnUsage,
                    sessionUsage = msg.sessionUsage,
                )
            }
    }
}
```

- [ ] **Step 3: Replace ChatComponent.kt**

```kotlin
package com.ai.challenge.ui.chat

import com.ai.challenge.core.chat.BranchService
import com.ai.challenge.core.chat.ChatService
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.UsageService
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
    chatService: ChatService,
    sessionService: SessionService,
    usageService: UsageService,
    branchService: BranchService,
    sessionId: AgentSessionId,
) : ComponentContext by componentContext {

    private val store = instanceKeeper.getStore {
        ChatStoreFactory(
            storeFactory = storeFactory,
            chatService = chatService,
            sessionService = sessionService,
            usageService = usageService,
            branchService = branchService,
        ).create()
    }

    init {
        store.accept(ChatStore.Intent.LoadSession(sessionId = sessionId))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ChatStore.State> = store.stateFlow

    fun onSendMessage(text: String) {
        store.accept(ChatStore.Intent.SendMessage(text = text))
    }

    fun onCreateBranch(parentTurnId: TurnId) {
        store.accept(ChatStore.Intent.CreateBranch(sourceTurnId = parentTurnId))
    }

    fun onSwitchBranch(branchId: BranchId) {
        store.accept(ChatStore.Intent.SwitchBranch(branchId = branchId))
    }

    fun onDeleteBranch(branchId: BranchId) {
        store.accept(ChatStore.Intent.DeleteBranch(branchId = branchId))
    }

    fun refreshBranches() {
        store.accept(ChatStore.Intent.LoadBranches)
    }
}
```

- [ ] **Step 4: Replace ChatContent.kt — flat branch list, remove tree rendering and dialog**

Replace the entire content of `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt` with:

```kotlin
package com.ai.challenge.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.model.UsageRecord
import com.ai.challenge.ui.model.UiMessage

@Composable
fun ChatContent(component: ChatComponent) {
    val state by component.state.collectAsState()
    var inputText by remember { mutableStateOf(value = "") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(index = state.messages.size - 1)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(weight = 1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(weight = 1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                items(items = state.messages) { message ->
                    val usage = message.turnId?.let { state.turnUsage[it] }
                    MessageBubble(
                        message = message,
                        usage = usage,
                        isBranchingEnabled = state.isBranchingEnabled,
                        onCreateBranch = { turnId ->
                            component.onCreateBranch(parentTurnId = turnId)
                        },
                    )
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
                    .padding(all = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(weight = 1f)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && inputText.isNotBlank() && !state.isLoading) {
                                component.onSendMessage(text = inputText.trim())
                                inputText = ""
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = { Text(text = "Type a message...") },
                    enabled = !state.isLoading,
                    singleLine = true,
                )
                TextButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            component.onSendMessage(text = inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !state.isLoading,
                ) {
                    Text(text = "Send")
                }
            }
            if (state.sessionUsage.totalTokens.value > 0) {
                SessionMetricsBar(usage = state.sessionUsage)
            }
        }

        if (state.isBranchingEnabled && state.branches.isNotEmpty()) {
            VerticalDivider()
            BranchPanel(
                branches = state.branches,
                activeBranchId = state.activeBranchId,
                onSwitchBranch = { component.onSwitchBranch(branchId = it) },
                onDeleteBranch = { component.onDeleteBranch(branchId = it) },
            )
        }
    }
}

@Composable
private fun BranchPanel(
    branches: List<Branch>,
    activeBranchId: BranchId?,
    onSwitchBranch: (BranchId) -> Unit,
    onDeleteBranch: (BranchId) -> Unit,
) {
    Surface(
        modifier = Modifier.width(width = 260.dp).fillMaxHeight(),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(all = 16.dp)) {
            Text(
                text = "Branches",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(height = 12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(height = 12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
                for (branch in branches) {
                    BranchItem(
                        branch = branch,
                        isActive = branch.id == activeBranchId,
                        onSwitchBranch = onSwitchBranch,
                        onDeleteBranch = onDeleteBranch,
                    )
                }
            }
        }
    }
}

@Composable
private fun BranchItem(
    branch: Branch,
    isActive: Boolean,
    onSwitchBranch: (BranchId) -> Unit,
    onDeleteBranch: (BranchId) -> Unit,
) {
    val backgroundColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                          else MaterialTheme.colorScheme.surface
    val label = if (branch.isMain) "main" else "Branch #${branch.id.value.take(n = 6)}"

    Surface(
        onClick = { onSwitchBranch(branch.id) },
        shape = RoundedCornerShape(size = 8.dp),
        color = backgroundColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(weight = 1f),
            )
            if (!branch.isMain) {
                IconButton(
                    onClick = { onDeleteBranch(branch.id) },
                    modifier = Modifier.size(size = 24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete branch",
                        modifier = Modifier.size(size = 14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: UiMessage,
    usage: UsageRecord?,
    isBranchingEnabled: Boolean,
    onCreateBranch: (TurnId) -> Unit,
) {
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
                    .clip(shape = RoundedCornerShape(size = 12.dp))
                    .background(color = backgroundColor)
                    .padding(all = 12.dp),
                color = textColor,
            )
            if (!message.isUser && usage != null && usage.totalTokens.value > 0) {
                Text(
                    text = formatTurnMetrics(usage = usage),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            if (!message.isUser && isBranchingEnabled && message.turnId != null) {
                Surface(
                    onClick = { onCreateBranch(message.turnId) },
                    shape = RoundedCornerShape(size = 6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
                    ) {
                        Text(
                            text = "\u2442",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Branch",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionMetricsBar(usage: UsageRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = formatSessionMetrics(usage = usage),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatCost(value: java.math.BigDecimal): String =
    value.stripTrailingZeros().toPlainString()

private fun formatTurnMetrics(usage: UsageRecord): String {
    val parts = mutableListOf<String>()
    parts.add("\u2191${usage.promptTokens.value}")
    parts.add("\u2193${usage.completionTokens.value}")
    parts.add("cached:${usage.cachedTokens.value}")
    if (usage.reasoningTokens.value > 0) parts.add("reasoning:${usage.reasoningTokens.value}")
    parts.addAll(formatCostParts(usage = usage))
    return parts.joinToString(separator = "  ")
}

private fun formatSessionMetrics(usage: UsageRecord): String {
    val parts = mutableListOf<String>()
    parts.add("Session: \u2191${usage.promptTokens.value}  \u2193${usage.completionTokens.value}  cached:${usage.cachedTokens.value}")
    parts.addAll(formatCostParts(usage = usage))
    return parts.joinToString(separator = "  |  ")
}

private fun formatCostParts(usage: UsageRecord): List<String> = buildList {
    add("cost:$${formatCost(value = usage.totalCost.value)}")
    if (usage.upstreamCost.value > java.math.BigDecimal.ZERO && usage.upstreamCost.value != usage.totalCost.value) add("upstream:$${formatCost(value = usage.upstreamCost.value)}")
    add("prompt:$${formatCost(value = usage.upstreamPromptCost.value)}")
    add("completion:$${formatCost(value = usage.upstreamCompletionsCost.value)}")
}
```

- [ ] **Step 5: Commit**

```bash
git add modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatComponent.kt modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt
git commit -m "refactor(ui): flat branch panel, activeBranchId as local state, auto-switch on create"
```

---

### Task 10: Update DI — AppModule

**Files:**
- Modify: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt`

- [ ] **Step 1: Replace AppModule.kt**

`AiSessionService` no longer takes `contextManager`. Update the wiring:

```kotlin
package com.ai.challenge.app.di

import com.ai.challenge.agent.AiBranchService
import com.ai.challenge.agent.AiChatService
import com.ai.challenge.agent.AiSessionService
import com.ai.challenge.agent.AiUsageService
import com.ai.challenge.context.BranchingContextManager
import com.ai.challenge.context.ContextCompressor
import com.ai.challenge.context.DefaultContextManager
import com.ai.challenge.context.FactExtractor
import com.ai.challenge.context.LlmContextCompressor
import com.ai.challenge.context.LlmFactExtractor
import com.ai.challenge.core.chat.BranchService
import com.ai.challenge.core.chat.ChatService
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.usage.UsageService
import com.ai.challenge.fact.repository.ExposedFactRepository
import com.ai.challenge.fact.repository.createFactDatabase
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.session.repository.ExposedAgentSessionRepository
import com.ai.challenge.session.repository.createSessionDatabase
import com.ai.challenge.summary.repository.ExposedSummaryRepository
import com.ai.challenge.summary.repository.createSummaryDatabase
import org.koin.dsl.module

val appModule = module {
    single {
        OpenRouterService(
            apiKey = System.getenv("OPENROUTER_API_KEY")
                ?: error("OPENROUTER_API_KEY environment variable is not set"),
        )
    }

    // Repositories
    single<AgentSessionRepository> { ExposedAgentSessionRepository(database = createSessionDatabase()) }
    single<FactRepository> { ExposedFactRepository(database = createFactDatabase()) }
    single<SummaryRepository> { ExposedSummaryRepository(database = createSummaryDatabase()) }

    // Context management
    single<ContextCompressor> { LlmContextCompressor(service = get(), model = "google/gemini-2.0-flash-001") }
    single<FactExtractor> { LlmFactExtractor(service = get(), model = "google/gemini-2.0-flash-001") }
    single { BranchingContextManager(repository = get()) }
    single<ContextManager> {
        DefaultContextManager(
            repository = get(),
            compressor = get(),
            summaryRepository = get(),
            factExtractor = get(),
            factRepository = get(),
            branchingContextManager = get(),
        )
    }

    // Domain services
    single<ChatService> { AiChatService(service = get(), model = "google/gemini-2.0-flash-001", repository = get(), contextManager = get()) }
    single<SessionService> { AiSessionService(repository = get()) }
    single<BranchService> { AiBranchService(repository = get()) }
    single<UsageService> { AiUsageService(repository = get()) }
}
```

- [ ] **Step 2: Commit**

```bash
git add modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt
git commit -m "refactor(di): update AppModule — AiSessionService no longer needs contextManager"
```

---

### Task 11: Update test fakes and context manager tests

**Files:**
- Modify: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/TestFakes.kt`
- Modify: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/BranchingContextManagerTest.kt`
- Modify: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt`

- [ ] **Step 1: Replace TestFakes.kt**

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.TurnSequence
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.shared.UpdatedAt
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.model.Cost
import com.ai.challenge.core.usage.model.TokenCount
import com.ai.challenge.core.usage.model.UsageRecord
import java.math.BigDecimal
import kotlin.time.Clock

internal val ZERO_USAGE = UsageRecord(
    promptTokens = TokenCount(value = 0),
    completionTokens = TokenCount(value = 0),
    cachedTokens = TokenCount(value = 0),
    cacheWriteTokens = TokenCount(value = 0),
    reasoningTokens = TokenCount(value = 0),
    totalCost = Cost(value = BigDecimal.ZERO),
    upstreamCost = Cost(value = BigDecimal.ZERO),
    upstreamPromptCost = Cost(value = BigDecimal.ZERO),
    upstreamCompletionsCost = Cost(value = BigDecimal.ZERO),
)

internal class InMemoryAgentSessionRepository : AgentSessionRepository {
    private val sessions = mutableMapOf<AgentSessionId, AgentSession>()
    private val branches = mutableMapOf<BranchId, Branch>()
    private val turnsByBranch = mutableMapOf<BranchId, MutableList<Turn>>()
    private val turnsById = mutableMapOf<TurnId, Turn>()

    fun addSession(session: AgentSession) {
        sessions[session.id] = session
    }

    override suspend fun save(session: AgentSession): AgentSession {
        sessions[session.id] = session
        return session
    }
    override suspend fun get(id: AgentSessionId): AgentSession? = sessions[id]
    override suspend fun delete(id: AgentSessionId) { sessions.remove(id) }
    override suspend fun list(): List<AgentSession> = sessions.values.toList()
    override suspend fun update(session: AgentSession): AgentSession {
        sessions[session.id] = session
        return session
    }

    override suspend fun createBranch(branch: Branch): Branch {
        branches[branch.id] = branch
        turnsByBranch.putIfAbsent(branch.id, mutableListOf())
        return branch
    }
    override suspend fun getBranches(sessionId: AgentSessionId): List<Branch> =
        branches.values.filter { it.sessionId == sessionId }
    override suspend fun getBranch(branchId: BranchId): Branch? = branches[branchId]
    override suspend fun getMainBranch(sessionId: AgentSessionId): Branch? =
        branches.values.firstOrNull { it.sessionId == sessionId && it.isMain }
    override suspend fun deleteBranch(branchId: BranchId) { branches.remove(branchId) }
    override suspend fun deleteTurnsByBranch(branchId: BranchId) { turnsByBranch[branchId]?.clear() }

    override suspend fun appendTurn(branchId: BranchId, turn: Turn): Turn {
        turnsById[turn.id] = turn
        turnsByBranch.getOrPut(branchId) { mutableListOf() }.add(turn)
        val branch = branches[branchId]
        if (branch != null) {
            branches[branchId] = branch.copy(turnSequence = TurnSequence(values = branch.turnSequence.values + turn.id))
        }
        return turn
    }
    override suspend fun getTurnsByBranch(branchId: BranchId): List<Turn> {
        val branch = branches[branchId] ?: return emptyList()
        return branch.turnSequence.values.mapNotNull { turnsById[it] }
    }
    override suspend fun getTurn(turnId: TurnId): Turn? = turnsById[turnId]
}

internal fun createTestSession(
    sessionId: AgentSessionId,
    contextManagementType: ContextManagementType,
): AgentSession {
    val now = Clock.System.now()
    return AgentSession(
        id = sessionId,
        title = SessionTitle(value = "test"),
        contextManagementType = contextManagementType,
        createdAt = CreatedAt(value = now),
        updatedAt = UpdatedAt(value = now),
    )
}

internal fun createTestBranch(
    id: BranchId,
    sessionId: AgentSessionId,
    sourceTurnId: TurnId?,
    turnIds: List<TurnId>,
): Branch {
    return Branch(
        id = id,
        sessionId = sessionId,
        sourceTurnId = sourceTurnId,
        turnSequence = TurnSequence(values = turnIds),
        createdAt = CreatedAt(value = Clock.System.now()),
    )
}

internal fun createTestTurn(
    sessionId: AgentSessionId,
    userMessage: String,
    assistantMessage: String,
): Turn {
    return Turn(
        id = TurnId.generate(),
        sessionId = sessionId,
        userMessage = MessageContent(value = userMessage),
        assistantMessage = MessageContent(value = assistantMessage),
        usage = ZERO_USAGE,
        createdAt = CreatedAt(value = Clock.System.now()),
    )
}
```

- [ ] **Step 2: Replace BranchingContextManagerTest.kt**

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BranchingContextManagerTest {

    private val sessionId = AgentSessionId(value = "session-1")

    private fun buildManager(repo: InMemoryAgentSessionRepository): BranchingContextManager =
        BranchingContextManager(repository = repo)

    @Test
    fun `prepareContext for main branch with turns`() = runTest {
        val repo = InMemoryAgentSessionRepository()
        val mainBranchId = BranchId.generate()

        repo.addSession(createTestSession(sessionId = sessionId, contextManagementType = ContextManagementType.Branching))

        val turn1 = createTestTurn(sessionId = sessionId, userMessage = "user1", assistantMessage = "assistant1")
        val turn2 = createTestTurn(sessionId = sessionId, userMessage = "user2", assistantMessage = "assistant2")

        repo.createBranch(branch = createTestBranch(id = mainBranchId, sessionId = sessionId, sourceTurnId = null, turnIds = emptyList()))
        repo.appendTurn(branchId = mainBranchId, turn = turn1)
        repo.appendTurn(branchId = mainBranchId, turn = turn2)

        val manager = buildManager(repo = repo)
        val result = manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = MessageContent(value = "newMessage"))

        assertFalse(result.compressed)
        assertEquals(2, result.originalTurnCount)
        assertEquals(2, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(5, result.messages.size)
        assertEquals(MessageRole.User, result.messages[0].role)
        assertEquals(MessageContent(value = "user1"), result.messages[0].content)
        assertEquals(MessageRole.Assistant, result.messages[1].role)
        assertEquals(MessageContent(value = "assistant1"), result.messages[1].content)
        assertEquals(MessageRole.User, result.messages[2].role)
        assertEquals(MessageContent(value = "user2"), result.messages[2].content)
        assertEquals(MessageRole.Assistant, result.messages[3].role)
        assertEquals(MessageContent(value = "assistant2"), result.messages[3].content)
        assertEquals(MessageRole.User, result.messages[4].role)
        assertEquals(MessageContent(value = "newMessage"), result.messages[4].content)
    }

    @Test
    fun `prepareContext for child branch includes trunk`() = runTest {
        val repo = InMemoryAgentSessionRepository()
        val mainBranchId = BranchId.generate()
        val childBranchId = BranchId.generate()

        repo.addSession(createTestSession(sessionId = sessionId, contextManagementType = ContextManagementType.Branching))

        val turn1 = createTestTurn(sessionId = sessionId, userMessage = "main-user1", assistantMessage = "main-assistant1")
        val turn2 = createTestTurn(sessionId = sessionId, userMessage = "main-user2", assistantMessage = "main-assistant2")
        val turn3 = createTestTurn(sessionId = sessionId, userMessage = "main-user3", assistantMessage = "main-assistant3")
        val childTurn1 = createTestTurn(sessionId = sessionId, userMessage = "child-user1", assistantMessage = "child-assistant1")

        repo.createBranch(branch = createTestBranch(id = mainBranchId, sessionId = sessionId, sourceTurnId = null, turnIds = emptyList()))
        repo.appendTurn(branchId = mainBranchId, turn = turn1)
        repo.appendTurn(branchId = mainBranchId, turn = turn2)
        repo.appendTurn(branchId = mainBranchId, turn = turn3)

        repo.createBranch(branch = createTestBranch(id = childBranchId, sessionId = sessionId, sourceTurnId = turn2.id, turnIds = listOf(turn1.id, turn2.id)))
        repo.appendTurn(branchId = childBranchId, turn = childTurn1)

        val manager = buildManager(repo = repo)
        val result = manager.prepareContext(sessionId = sessionId, branchId = childBranchId, newMessage = MessageContent(value = "new"))

        assertEquals(7, result.messages.size)
        assertEquals(MessageContent(value = "main-user1"), result.messages[0].content)
        assertEquals(MessageContent(value = "main-assistant1"), result.messages[1].content)
        assertEquals(MessageContent(value = "main-user2"), result.messages[2].content)
        assertEquals(MessageContent(value = "main-assistant2"), result.messages[3].content)
        assertEquals(MessageContent(value = "child-user1"), result.messages[4].content)
        assertEquals(MessageContent(value = "child-assistant1"), result.messages[5].content)
        assertEquals(MessageContent(value = "new"), result.messages[6].content)
        assertEquals(3, result.originalTurnCount)
        assertEquals(3, result.retainedTurnCount)
    }

    @Test
    fun `prepareContext for empty branch with trunk`() = runTest {
        val repo = InMemoryAgentSessionRepository()
        val mainBranchId = BranchId.generate()
        val childBranchId = BranchId.generate()

        repo.addSession(createTestSession(sessionId = sessionId, contextManagementType = ContextManagementType.Branching))

        val mainTurn1 = createTestTurn(sessionId = sessionId, userMessage = "main1", assistantMessage = "main-resp1")
        val mainTurn2 = createTestTurn(sessionId = sessionId, userMessage = "main2", assistantMessage = "main-resp2")

        repo.createBranch(branch = createTestBranch(id = mainBranchId, sessionId = sessionId, sourceTurnId = null, turnIds = emptyList()))
        repo.appendTurn(branchId = mainBranchId, turn = mainTurn1)
        repo.appendTurn(branchId = mainBranchId, turn = mainTurn2)

        repo.createBranch(branch = createTestBranch(id = childBranchId, sessionId = sessionId, sourceTurnId = mainTurn1.id, turnIds = listOf(mainTurn1.id)))

        val manager = buildManager(repo = repo)
        val result = manager.prepareContext(sessionId = sessionId, branchId = childBranchId, newMessage = MessageContent(value = "new"))

        assertEquals(3, result.messages.size)
        assertEquals(MessageContent(value = "main1"), result.messages[0].content)
        assertEquals(MessageContent(value = "main-resp1"), result.messages[1].content)
        assertEquals(MessageContent(value = "new"), result.messages[2].content)
        assertEquals(1, result.originalTurnCount)
        assertEquals(1, result.retainedTurnCount)
        assertFalse(result.compressed)
    }
}
```

- [ ] **Step 3: Update DefaultContextManagerTest.kt**

Update `setupSession` and `createManager` and `saveTurns` to remove `activeBranchId` parameter and pass `branchId` to `prepareContext`. The key changes:

In `setupSession`: replace `createTestSession(sessionId = sessionId, contextManagementType = type, activeBranchId = mainBranchId)` with `createTestSession(sessionId = sessionId, contextManagementType = type)`.

In every `prepareContext` call: change from `manager.prepareContext(sessionId = sessionId, newMessage = ...)` to `manager.prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage = ...)`.

This is a mechanical find-and-replace across the test file. Every `prepareContext(sessionId = sessionId, newMessage =` becomes `prepareContext(sessionId = sessionId, branchId = mainBranchId, newMessage =`.

- [ ] **Step 4: Run context manager tests**

Run: `./gradlew :modules:domain:context-manager:test -q`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/TestFakes.kt modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/BranchingContextManagerTest.kt modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt
git commit -m "test: update context manager tests for flat branching model"
```

---

### Task 12: Update repository test

**Files:**
- Modify: `modules/data/session-repository-exposed/src/test/kotlin/com/ai/challenge/session/repository/ExposedAgentSessionRepositoryTest.kt`

- [ ] **Step 1: Update ExposedAgentSessionRepositoryTest.kt**

Replace `createSession` helper — remove `activeBranchId`:

```kotlin
private fun createSession(title: String): AgentSession {
    val now = Clock.System.now()
    return AgentSession(
        id = AgentSessionId.generate(),
        title = SessionTitle(value = title),
        contextManagementType = ContextManagementType.None,
        createdAt = CreatedAt(value = now),
        updatedAt = UpdatedAt(value = now),
    )
}
```

Update `appendTurn and getTurn round-trip` test — create Branch with flat model:

```kotlin
val branch = Branch(
    id = BranchId.generate(),
    sessionId = session.id,
    sourceTurnId = null,
    turnSequence = TurnSequence(values = emptyList()),
    createdAt = session.createdAt,
)
```

Update `createBranch and getBranches round-trip` test similarly. Remove assertion for `branches[0].name.value` (no name anymore). Replace with assertion on `branches[0].isMain`.

Add imports: `import com.ai.challenge.core.branch.TurnSequence`. Remove import of `BranchName`.

- [ ] **Step 2: Run repository tests**

Run: `./gradlew :modules:data:session-repository-exposed:test -q`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add modules/data/session-repository-exposed/src/test/kotlin/com/ai/challenge/session/repository/ExposedAgentSessionRepositoryTest.kt
git commit -m "test: update repository tests for flat branching schema"
```

---

### Task 13: Update ChatStoreTest and FakeServices

**Files:**
- Modify: `modules/presentation/compose-ui/src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt`

- [ ] **Step 1: Update FakeServices in ChatStoreTest.kt**

The `FakeServices` class needs to:
1. Remove `activeBranchId` from `AgentSession` construction
2. Change `ChatService.send()` signature to include `branchId`
3. Change `BranchService` methods — remove `switch`, `getActive`, `getActiveTurns`, `getParentMap`; add `getTurns`
4. Store a main branch so `getTurns` works

Replace the `FakeServices` class and update the `send` call in `SendMessage` tests to pass `branchId`. The `handleLoadSession` in the store now calls `branchService.getTurns(branchId)` instead of `branchService.getActiveTurns(sessionId)`, so `FakeServices.getTurns` must return turns by branch.

Key changes to `FakeServices`:

```kotlin
open class FakeServices(
    private val sendTurnId: TurnId = TurnId.generate(),
    private val sendAssistantMessage: String = "",
    private val sendUsage: UsageRecord = emptyUsage(),
    private val sendError: DomainError? = null,
) : ChatService, SessionService, UsageService, BranchService {

    private val sessions = ConcurrentHashMap<AgentSessionId, AgentSession>()
    private val turns = ConcurrentHashMap<TurnId, Pair<AgentSessionId, Turn>>()
    private val usageData = ConcurrentHashMap<TurnId, Pair<AgentSessionId, UsageRecord>>()
    private val mainBranches = ConcurrentHashMap<AgentSessionId, BranchId>()

    // -- ChatService --
    override suspend fun send(sessionId: AgentSessionId, branchId: BranchId, message: MessageContent): Either<DomainError, Turn> {
        if (sendError != null) return Either.Left(value = sendError)
        val turn = Turn(
            id = sendTurnId,
            sessionId = sessionId,
            userMessage = message,
            assistantMessage = MessageContent(value = sendAssistantMessage),
            usage = sendUsage,
            createdAt = CreatedAt(value = Clock.System.now()),
        )
        appendTurnDirect(sessionId = sessionId, turn = turn)
        recordUsageDirect(turnId = sendTurnId, sessionId = sessionId, usage = sendUsage)
        return Either.Right(value = turn)
    }

    // -- SessionService --
    override suspend fun create(title: SessionTitle): Either<DomainError, AgentSession> {
        val id = AgentSessionId.generate()
        val branchId = BranchId.generate()
        val now = Clock.System.now()
        val session = AgentSession(
            id = id,
            title = title,
            contextManagementType = ContextManagementType.None,
            createdAt = CreatedAt(value = now),
            updatedAt = UpdatedAt(value = now),
        )
        sessions[id] = session
        mainBranches[id] = branchId
        return Either.Right(value = session)
    }

    // ... get, delete, list, updateTitle, updateContextManagementType remain same but without activeBranchId ...

    // -- BranchService --
    override suspend fun create(sessionId: AgentSessionId, sourceTurnId: TurnId, fromBranchId: BranchId): Either<DomainError, Branch> =
        Either.Left(value = DomainError.ApiError(message = "Not implemented"))

    override suspend fun delete(branchId: BranchId): Either<DomainError, Unit> =
        Either.Left(value = DomainError.ApiError(message = "Not implemented"))

    override suspend fun getAll(sessionId: AgentSessionId): Either<DomainError, List<Branch>> =
        Either.Right(value = emptyList())

    override suspend fun getTurns(branchId: BranchId): Either<DomainError, List<Turn>> {
        val all = turns.values.map { it.second }.sortedBy { it.createdAt.value }
        return Either.Right(value = all)
    }
}
```

Remove imports of `BranchName`. Update all session creation in `FakeServices` to not include `activeBranchId`.

- [ ] **Step 2: Run ChatStore tests**

Run: `./gradlew :modules:presentation:compose-ui:test -q`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add modules/presentation/compose-ui/src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt
git commit -m "test: update ChatStoreTest for flat branching — new FakeServices, branchId in send"
```

---

### Task 14: Full build and test verification

- [ ] **Step 1: Run full build**

Run: `./gradlew build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all tests**

Run: `./gradlew test -q`
Expected: All tests PASS

- [ ] **Step 3: Fix any compilation errors**

If there are remaining references to `BranchName`, `activeBranchId`, `parentId`, `withActiveBranch`, `switch`, `getActive`, `getActiveTurns`, `getParentMap`, `getTurns(sessionId`, `branchParentMap` — find and fix them.

Run: `grep -rn "BranchName\|activeBranchId\|\.parentId\|withActiveBranch\|branchParentMap\|getParentMap\|getActiveTurns\|\.switch(" modules/*/src/main/ modules/*/src/test/ --include="*.kt" | grep -v ".gradle" | grep -v "build/"`

Expected: No results

- [ ] **Step 4: Delete existing SQLite database (schema changed)**

The database file needs to be recreated since columns were removed/added. The `SchemaUtils.createMissingTablesAndColumns` in the init block will create the new schema, but the old database file will have incompatible columns.

Run: `rm -f ~/.ai-challenge/*.db 2>/dev/null; echo "Old DB files cleaned"`

Note: Check the actual DB location by searching for `createSessionDatabase` function.

- [ ] **Step 5: Final commit if any fixes were needed**

```bash
git add -A
git commit -m "fix: resolve remaining compilation issues from flat branching migration"
```
