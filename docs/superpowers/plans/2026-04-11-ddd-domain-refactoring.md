# DDD Domain Models Refactoring

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 10 DDD violations in core domain models — eliminate default values, add missing aggregate references, unify error handling, and split the God Object Agent interface.

**Architecture:** Each task is an isolated refactoring step that preserves behavior. Tasks are ordered by dependency: foundational changes first (default values, class extraction), then model enrichment (sessionId fields, Branch encapsulation), then API cleanup (repository signatures, error handling), and finally the Agent interface split.

**Tech Stack:** Kotlin 2.3, Arrow Either, Exposed ORM, MVIKotlin, Decompose, Koin DI

**Branch:** `refactor/ddd-domain-models` from `develop`

---

### Task 1: Remove default parameter values

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/session/AgentSession.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/turn/Turn.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/summary/Summary.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/token/TokenDetails.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/cost/CostDetails.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/agent/Agent.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/session/AgentSessionRepository.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/turn/TurnRepository.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManager.kt` (PreparedContext defaults)
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/cost/CostDetailsRepository.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/token/TokenDetailsRepository.kt`
- Modify: all call sites across data/, domain/, presentation/ modules
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/sessionlist/store/SessionListStore.kt`
- Modify: all test files that construct these models

- [ ] **Step 1: Remove defaults from core domain models**

Remove all default parameter values from data classes:

`AgentSession.kt`:
```kotlin
data class AgentSession(
    val id: AgentSessionId,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

`Turn.kt`:
```kotlin
data class Turn(
    val id: TurnId,
    val userMessage: String,
    val agentResponse: String,
    val timestamp: Instant,
)
```

`Summary.kt`:
```kotlin
data class Summary(
    val id: SummaryId,
    val text: String,
    val fromTurnIndex: Int,
    val toTurnIndex: Int,
    val createdAt: Instant,
)
```

`TokenDetails.kt` — remove defaults from constructor but keep `plus` operator and `totalTokens`:
```kotlin
data class TokenDetails(
    val promptTokens: Int,
    val completionTokens: Int,
    val cachedTokens: Int,
    val cacheWriteTokens: Int,
    val reasoningTokens: Int,
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

`CostDetails.kt` — same pattern:
```kotlin
data class CostDetails(
    val totalCost: Double,
    val upstreamCost: Double,
    val upstreamPromptCost: Double,
    val upstreamCompletionsCost: Double,
) {
    operator fun plus(other: CostDetails) = CostDetails(
        totalCost = totalCost + other.totalCost,
        upstreamCost = upstreamCost + other.upstreamCost,
        upstreamPromptCost = upstreamPromptCost + other.upstreamPromptCost,
        upstreamCompletionsCost = upstreamCompletionsCost + other.upstreamCompletionsCost,
    )
}
```

- [ ] **Step 2: Remove defaults from interfaces**

`Agent.kt` — remove defaults from `createSession` and `getTurns`:
```kotlin
suspend fun createSession(title: String): AgentSessionId
suspend fun getTurns(sessionId: AgentSessionId, limit: Int?): List<Turn>
```

`AgentSessionRepository.kt`:
```kotlin
suspend fun create(title: String): AgentSessionId
```

`TurnRepository.kt`:
```kotlin
suspend fun getBySession(sessionId: AgentSessionId, limit: Int?): List<Turn>
```

- [ ] **Step 3: Fix all call sites in AiAgent.kt**

In `AiAgent.kt`:
- Line 76: `Turn(userMessage = message, agentResponse = text)` → `Turn(id = TurnId.generate(), userMessage = message, agentResponse = text, timestamp = Clock.System.now())`
- Line 249: `TokenDetails(promptTokens = ..., completionTokens = ..., cachedTokens = ..., cacheWriteTokens = ..., reasoningTokens = ...)` — already explicit, no change needed.
- Line 257: `CostDetails(totalCost = ..., ...)` — already explicit, no change needed.
- Line 96: `createSession(title: String)` → `sessionRepository.create(title)` — title has no default now, verify all callers pass it.

- [ ] **Step 4: Fix call sites in presentation layer**

`ChatStore.kt` line 29: `sessionTokens: TokenDetails = TokenDetails()` → `sessionTokens: TokenDetails = TokenDetails(promptTokens = 0, completionTokens = 0, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0)`

`ChatStore.kt` line 30: `sessionCosts: CostDetails = CostDetails()` → `sessionCosts: CostDetails = CostDetails(totalCost = 0.0, upstreamCost = 0.0, upstreamPromptCost = 0.0, upstreamCompletionsCost = 0.0)`

`ChatStoreFactory.kt` line 97-98: `fold(TokenDetails())` → `fold(TokenDetails(promptTokens = 0, completionTokens = 0, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0))`
Same for `CostDetails()` on line 98.
Same pattern on lines 199-200.

`SessionListStore.kt` line 16-17: `State()` has defaults for `sessions` and `activeSessionId` — these are UI state defaults (emptyList, null), not domain model defaults. Keep them as-is since `State` is a UI model, not a domain model.

`ChatStore.kt` `State` class — same reasoning. UI state defaults are acceptable (emptyList, false, null, emptyMap). Only fix the `TokenDetails()` and `CostDetails()` calls within it.

`SessionListStoreFactory.kt` line 58: `agent.createSession()` → `agent.createSession(title = "")`

`RootComponent.kt` line 56: `agent.createSession()` → `agent.createSession(title = "")`
Line 73: `agent.createSession()` → `agent.createSession(title = "")`

- [ ] **Step 5: Fix call sites in data repositories**

Search all Exposed repositories for `TokenDetails(`, `CostDetails(`, `Turn(`, `Summary(`, `AgentSession(` constructors and add any missing named arguments. Each repository maps from ResultRow to domain models — ensure all fields are passed explicitly.

In `ExposedTurnRepository`: Turn construction must include `id = TurnId(...)`, `timestamp = ...` explicitly.
In `ExposedSessionRepository`: AgentSession construction must include all 4 fields explicitly.
In `ExposedSummaryRepository`: Summary construction must include `id = SummaryId(...)`, `createdAt = ...`.
In `ExposedTokenRepository`: TokenDetails with all 5 fields.
In `ExposedCostRepository`: CostDetails with all 4 fields.

- [ ] **Step 6: Fix call sites in context-manager module**

In `DefaultContextManager.kt` — any `PreparedContext(...)` or `ContextMessage(...)` construction, ensure all args explicit.
In `BranchingContextManager.kt` — same.
In `LlmFactExtractor.kt` — `Fact(...)` construction, `FactId.generate()` calls.

- [ ] **Step 7: Fix all test files**

Update every test file that constructs domain models. Key files:
- `OpenRouterAgentTest.kt`: FakeSessionRepository, FakeTurnRepository, Turn/TokenDetails/CostDetails constructors
- `DefaultContextManagerTest.kt`: Summary, Fact, Turn, TokenDetails constructors
- `BranchingContextManagerTest.kt`: Turn, Branch constructors
- `LlmFactExtractorTest.kt`: Fact constructors
- `ChatStoreTest.kt`: FakeAgent, Turn, TokenDetails, CostDetails, AgentSession constructors
- `SessionListStoreTest.kt`: FakeAgent, AgentSession constructors
- All Exposed repository tests: model constructors

- [ ] **Step 8: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — all compilation and tests pass.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor: remove all default parameter values from domain models and interfaces"
```

---

### Task 2: Extract nested classes from ContextManager

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManager.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/context/PreparedContext.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextMessage.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/context/MessageRole.kt`
- Modify: all files importing `ContextManager.PreparedContext` or nested types

- [ ] **Step 1: Create MessageRole.kt**

```kotlin
package com.ai.challenge.core.context

enum class MessageRole {
    System,
    User,
    Assistant,
}
```

- [ ] **Step 2: Create ContextMessage.kt**

```kotlin
package com.ai.challenge.core.context

data class ContextMessage(
    val role: MessageRole,
    val content: String,
)
```

- [ ] **Step 3: Create PreparedContext.kt**

```kotlin
package com.ai.challenge.core.context

data class PreparedContext(
    val messages: List<ContextMessage>,
    val compressed: Boolean,
    val originalTurnCount: Int,
    val retainedTurnCount: Int,
    val summaryCount: Int,
)
```

- [ ] **Step 4: Simplify ContextManager.kt**

Remove all nested classes, keep only the interface:
```kotlin
package com.ai.challenge.core.context

import com.ai.challenge.core.session.AgentSessionId

interface ContextManager {
    suspend fun prepareContext(
        sessionId: AgentSessionId,
        newMessage: String,
    ): PreparedContext
}
```

- [ ] **Step 5: Update imports across the codebase**

Replace all occurrences of:
- `ContextManager.PreparedContext.ContextMessage.MessageRole` → `com.ai.challenge.core.context.MessageRole`
- `ContextManager.PreparedContext.ContextMessage` → `com.ai.challenge.core.context.ContextMessage`
- `ContextManager.PreparedContext` → `com.ai.challenge.core.context.PreparedContext`

Key files to update:
- `AiAgent.kt` line 16: `import com.ai.challenge.core.context.ContextManager.PreparedContext.ContextMessage.MessageRole` → `import com.ai.challenge.core.context.MessageRole`
- `DefaultContextManager.kt`: all references to nested types
- `BranchingContextManager.kt`: PreparedContext and ContextMessage references
- `LlmContextCompressor.kt`: if it references these types
- All context-manager test files

Also update `AiAgent.kt` extension function:
```kotlin
fun MessageRole.toApiRole(): String = when (this) {
    MessageRole.System -> "system"
    MessageRole.User -> "user"
    MessageRole.Assistant -> "assistant"
}
```

- [ ] **Step 6: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: extract PreparedContext, ContextMessage, MessageRole from ContextManager interface"
```

---

### Task 3: Add sessionId to Turn

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/turn/Turn.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/turn/TurnRepository.kt`
- Modify: `modules/data/turn-repository-exposed/src/main/kotlin/.../ExposedTurnRepository.kt`
- Modify: `modules/domain/ai-agent/src/main/kotlin/.../AiAgent.kt`
- Modify: `modules/domain/context-manager/src/main/kotlin/.../DefaultContextManager.kt`
- Modify: `modules/domain/context-manager/src/main/kotlin/.../BranchingContextManager.kt`
- Modify: all test files constructing Turn

- [ ] **Step 1: Add sessionId to Turn**

```kotlin
package com.ai.challenge.core.turn

import com.ai.challenge.core.session.AgentSessionId
import kotlin.time.Instant

data class Turn(
    val id: TurnId,
    val sessionId: AgentSessionId,
    val userMessage: String,
    val agentResponse: String,
    val timestamp: Instant,
)
```

- [ ] **Step 2: Update TurnRepository interface**

Remove `sessionId` from `append` — it's now in the Turn itself:
```kotlin
interface TurnRepository {
    suspend fun append(turn: Turn): TurnId
    suspend fun getBySession(sessionId: AgentSessionId, limit: Int?): List<Turn>
    suspend fun get(turnId: TurnId): Turn?
}
```

- [ ] **Step 3: Update ExposedTurnRepository**

Update `append` to read sessionId from Turn:
```kotlin
override suspend fun append(turn: Turn): TurnId {
    val turnId = turn.id
    transaction(db = database) {
        TurnsTable.insert {
            it[id] = turnId.value
            it[sessionId] = turn.sessionId.value
            it[userMessage] = turn.userMessage
            it[agentResponse] = turn.agentResponse
            it[timestamp] = turn.timestamp.toString()
        }
    }
    return turnId
}
```

Update `toTurn` mapping to include sessionId:
```kotlin
private fun ResultRow.toTurn() = Turn(
    id = TurnId(value = this[TurnsTable.id]),
    sessionId = AgentSessionId(value = this[TurnsTable.sessionId]),
    userMessage = this[TurnsTable.userMessage],
    agentResponse = this[TurnsTable.agentResponse],
    timestamp = Instant.parse(this[TurnsTable.timestamp]),
)
```

- [ ] **Step 4: Update AiAgent.kt**

Line 76-77, change Turn construction and append call:
```kotlin
val turn = Turn(
    id = TurnId.generate(),
    sessionId = sessionId,
    userMessage = message,
    agentResponse = text,
    timestamp = Clock.System.now(),
)
val turnId = turnRepository.append(turn = turn)
```

- [ ] **Step 5: Update context-manager files and all tests**

Update all Turn constructors in:
- `DefaultContextManager.kt`, `BranchingContextManager.kt` — if they construct Turn objects
- `TestFakes.kt` — InMemoryTurnRepository.append signature change
- `OpenRouterAgentTest.kt` — FakeTurnRepository and Turn construction
- `DefaultContextManagerTest.kt` — all Turn construction
- `BranchingContextManagerTest.kt` — all Turn construction
- `ChatStoreTest.kt` — FakeAgent Turn construction
- `ExposedTurnRepositoryTest.kt` — test Turn construction

Every Turn constructor must now include `sessionId = <value>`.

- [ ] **Step 6: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: add sessionId to Turn, remove sessionId param from TurnRepository.append"
```

---

### Task 4: Add sessionId to Fact and Summary

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/Fact.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactRepository.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/summary/Summary.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/summary/SummaryRepository.kt`
- Modify: `modules/data/fact-repository-exposed/src/main/kotlin/.../ExposedFactRepository.kt`
- Modify: `modules/data/summary-repository-exposed/src/main/kotlin/.../ExposedSummaryRepository.kt`
- Modify: `modules/domain/context-manager/src/main/kotlin/.../DefaultContextManager.kt`
- Modify: `modules/domain/context-manager/src/main/kotlin/.../LlmFactExtractor.kt`
- Modify: all test files constructing Fact or Summary

- [ ] **Step 1: Add sessionId to Fact**

```kotlin
package com.ai.challenge.core.fact

import com.ai.challenge.core.session.AgentSessionId

data class Fact(
    val id: FactId,
    val sessionId: AgentSessionId,
    val category: FactCategory,
    val key: String,
    val value: String,
)
```

- [ ] **Step 2: Update FactRepository — remove sessionId from save**

```kotlin
interface FactRepository {
    suspend fun save(facts: List<Fact>)
    suspend fun getBySession(sessionId: AgentSessionId): List<Fact>
    suspend fun deleteBySession(sessionId: AgentSessionId)
}
```

Note: `save` no longer takes `sessionId` — it reads from each `Fact.sessionId`. All facts in a single `save` call should have the same sessionId (the implementation deletes existing facts for that session before inserting).

- [ ] **Step 3: Add sessionId to Summary**

```kotlin
package com.ai.challenge.core.summary

import com.ai.challenge.core.session.AgentSessionId
import kotlin.time.Instant

data class Summary(
    val id: SummaryId,
    val sessionId: AgentSessionId,
    val text: String,
    val fromTurnIndex: Int,
    val toTurnIndex: Int,
    val createdAt: Instant,
)
```

- [ ] **Step 4: Update SummaryRepository — remove sessionId from save**

```kotlin
interface SummaryRepository {
    suspend fun save(summary: Summary)
    suspend fun getBySession(sessionId: AgentSessionId): List<Summary>
}
```

- [ ] **Step 5: Update ExposedFactRepository and ExposedSummaryRepository**

`ExposedFactRepository.save`: read sessionId from `facts.first().sessionId` for the deleteWhere, and from each fact for batchInsert.

`ExposedSummaryRepository.save`: read sessionId from `summary.sessionId`.

- [ ] **Step 6: Update DefaultContextManager and LlmFactExtractor**

In `DefaultContextManager` — wherever `summaryRepository.save(sessionId, summary)` is called, change to `summaryRepository.save(summary = summary)` (Summary already contains sessionId).
Wherever `factRepository.save(sessionId, facts)` is called, change to `factRepository.save(facts = facts)`.

In `LlmFactExtractor` — if it creates Fact objects, add `sessionId` parameter to its interface/method and pass it through to Fact construction.

- [ ] **Step 7: Update all test files**

All Fact constructors must include `sessionId`. All Summary constructors must include `sessionId`.
Update fake repositories in TestFakes.kt, DefaultContextManagerTest.kt, LlmFactExtractorTest.kt, ExposedFactRepositoryTest.kt, ExposedSummaryRepositoryTest.kt.

- [ ] **Step 8: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor: add sessionId to Fact and Summary, remove sessionId from repository save methods"
```

---

### Task 5: Encapsulate turnIds in Branch, delete BranchTurnRepository

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/branch/Branch.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchRepository.kt`
- Delete: `modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchTurnRepository.kt`
- Delete: `modules/data/branch-turn-repository-exposed/` (entire module)
- Modify: `modules/data/branch-repository-exposed/src/main/kotlin/.../ExposedBranchRepository.kt`
- Modify: `modules/data/branch-repository-exposed/build.gradle.kts` (add SQLite dep if not present)
- Modify: `modules/domain/ai-agent/src/main/kotlin/.../AiAgent.kt`
- Modify: `modules/domain/context-manager/src/main/kotlin/.../BranchingContextManager.kt`
- Modify: `modules/presentation/app/src/main/kotlin/.../di/AppModule.kt`
- Modify: `modules/presentation/app/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: all test files

- [ ] **Step 1: Add turnIds to Branch**

```kotlin
package com.ai.challenge.core.branch

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import kotlin.time.Instant

data class Branch(
    val id: BranchId,
    val sessionId: AgentSessionId,
    val name: String,
    val parentBranchId: BranchId?,
    val isActive: Boolean,
    val turnIds: List<TurnId>,
    val createdAt: Instant,
) {
    val isMain: Boolean get() = parentBranchId == null
}
```

- [ ] **Step 2: Expand BranchRepository, delete BranchTurnRepository**

Updated `BranchRepository.kt`:
```kotlin
package com.ai.challenge.core.branch

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId

interface BranchRepository {
    suspend fun create(branch: Branch): BranchId
    suspend fun get(branchId: BranchId): Branch?
    suspend fun getBySession(sessionId: AgentSessionId): List<Branch>
    suspend fun getMainBranch(sessionId: AgentSessionId): Branch?
    suspend fun getActiveBranch(sessionId: AgentSessionId): Branch?
    suspend fun setActive(sessionId: AgentSessionId, branchId: BranchId)
    suspend fun delete(branchId: BranchId)
    suspend fun appendTurn(branchId: BranchId, turnId: TurnId)
    suspend fun deleteTurnsByBranch(branchId: BranchId)
}
```

Delete file: `modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchTurnRepository.kt`

- [ ] **Step 3: Update ExposedBranchRepository — absorb junction table**

Add `BranchTurnsTable` object inside the repository file. Load turnIds when constructing Branch. Implement `appendTurn` and `deleteTurnsByBranch`:

Add to ExposedBranchRepository.kt:
```kotlin
private object BranchTurnsTable : Table("branch_turns") {
    val branchId = varchar("branch_id", 36)
    val turnId = varchar("turn_id", 36)
    val orderIndex = integer("order_index")
}
```

Update `init` block to also create BranchTurnsTable.
Update `toBranch()` mapping to load turnIds via a join or subquery.
Add `appendTurn` and `deleteTurnsByBranch` implementations.

On `create(branch)`: also insert the branch's turnIds into BranchTurnsTable.
On `delete(branchId)`: also delete from BranchTurnsTable.
On `get/getBySession/getMainBranch/getActiveBranch`: load turnIds from BranchTurnsTable ordered by orderIndex.

- [ ] **Step 4: Delete branch-turn-repository-exposed module**

```bash
rm -rf modules/data/branch-turn-repository-exposed
```

Remove from `settings.gradle.kts`:
```kotlin
// Remove this line:
include(":modules:data:branch-turn-repository-exposed")
```

Remove from `modules/presentation/app/build.gradle.kts` — remove the dependency on `:modules:data:branch-turn-repository-exposed`.

- [ ] **Step 5: Update AppModule.kt**

Remove BranchTurnRepository binding and imports:
```kotlin
// Remove these lines:
// import com.ai.challenge.branch.turn.repository.ExposedBranchTurnRepository
// import com.ai.challenge.branch.turn.repository.createBranchTurnDatabase
// import com.ai.challenge.core.branch.BranchTurnRepository
// single<BranchTurnRepository> { ExposedBranchTurnRepository(createBranchTurnDatabase()) }
```

Remove `branchTurnRepository` from BranchingContextManager construction:
```kotlin
single {
    BranchingContextManager(
        turnRepository = get(),
        branchRepository = get(),
    )
}
```

Remove `branchTurnRepository = get()` from AiAgent construction.

- [ ] **Step 6: Update AiAgent.kt**

Remove `branchTurnRepository` from constructor. Replace all `branchTurnRepository` usages:

In `send()`: replace `branchTurnRepository.getMaxOrderIndex(...)` / `branchTurnRepository.append(...)` with:
```kotlin
if (contextType is ContextManagementType.Branching) {
    val activeBranch = branchRepository.getActiveBranch(sessionId = sessionId)
    if (activeBranch != null) {
        branchRepository.appendTurn(branchId = activeBranch.id, turnId = turnId)
    }
}
```

In `updateContextManagementType()`: replace loop with `branchTurnRepository.append` to `branchRepository.appendTurn`:
```kotlin
for ((index, turn) in turns.withIndex()) {
    branchRepository.appendTurn(branchId = mainBranch.id, turnId = turn.id)
}
```

But note: `create(branch = mainBranch)` now creates the branch with `turnIds = emptyList()`. The turns get appended one by one via `appendTurn`.

In `createBranch()`: replace `branchTurnRepository.getTurnIds(...)` with `branchRepository.get(fromBranchId)!!.turnIds`:
```kotlin
val parentBranch = branchRepository.get(branchId = fromBranchId)
    ?: raise(AgentError.ApiError(message = "Parent branch not found"))
val parentTurnIds = parentBranch.turnIds
val cutIndex = parentTurnIds.indexOf(element = parentTurnId)
val trunkTurnIds = if (cutIndex >= 0) parentTurnIds.subList(fromIndex = 0, toIndex = cutIndex + 1) else parentTurnIds
for (turnId in trunkTurnIds) {
    branchRepository.appendTurn(branchId = branch.id, turnId = turnId)
}
```

In `getActiveBranchTurns()`: replace `branchTurnRepository.getTurnIds(...)` with `activeBranch.turnIds`:
```kotlin
val turnIds = activeBranch.turnIds
turnIds.mapNotNull { turnRepository.get(turnId = it) }
```

In `cascadeDeleteBranch()`: replace `branchTurnRepository.deleteByBranch(...)` with `branchRepository.deleteTurnsByBranch(...)`.

- [ ] **Step 7: Update BranchingContextManager.kt**

Remove `branchTurnRepository` from constructor. Use `branch.turnIds` instead:
```kotlin
class BranchingContextManager(
    private val turnRepository: TurnRepository,
    private val branchRepository: BranchRepository,
) : ContextManager {
    // ...
    // Replace branchTurnRepository.getTurnIds(branchId) with branch.turnIds
}
```

- [ ] **Step 8: Update all test files**

- Remove `FakeBranchTurnRepository` from `OpenRouterAgentTest.kt` and `TestFakes.kt`
- Update `InMemoryBranchRepository` in `TestFakes.kt` to support `appendTurn`, `deleteTurnsByBranch`, and load `turnIds`
- All Branch constructors must include `turnIds = listOf(...)` or `turnIds = emptyList()`
- Update `ExposedBranchRepositoryTest.kt` to test new methods
- Delete `ExposedBranchTurnRepositoryTest.kt` (it's in the deleted module)

- [ ] **Step 9: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "refactor: encapsulate turnIds in Branch, delete BranchTurnRepository module"
```

---

### Task 6: Simplify Token/Cost repository signatures

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/token/TokenDetailsRepository.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/cost/CostDetailsRepository.kt`
- Modify: `modules/data/token-repository-exposed/src/main/kotlin/.../ExposedTokenRepository.kt`
- Modify: `modules/data/cost-repository-exposed/src/main/kotlin/.../ExposedCostRepository.kt`
- Modify: `modules/domain/ai-agent/src/main/kotlin/.../AiAgent.kt`
- Modify: test files for token/cost repositories and AiAgent

- [ ] **Step 1: Update TokenDetailsRepository interface**

```kotlin
interface TokenDetailsRepository {
    suspend fun record(turnId: TurnId, details: TokenDetails)
    suspend fun getByTurn(turnId: TurnId): TokenDetails?
    suspend fun getBySession(sessionId: AgentSessionId): Map<TurnId, TokenDetails>
    suspend fun getSessionTotal(sessionId: AgentSessionId): TokenDetails
}
```

- [ ] **Step 2: Update CostDetailsRepository interface**

```kotlin
interface CostDetailsRepository {
    suspend fun record(turnId: TurnId, details: CostDetails)
    suspend fun getByTurn(turnId: TurnId): CostDetails?
    suspend fun getBySession(sessionId: AgentSessionId): Map<TurnId, CostDetails>
    suspend fun getSessionTotal(sessionId: AgentSessionId): CostDetails
}
```

- [ ] **Step 3: Update Exposed implementations**

In `ExposedTokenRepository.record()`: remove sessionId parameter. The Turn already knows its session (from Task 3). The table may still store sessionId for query efficiency — derive it from a join or keep the column but populate it by looking up the turn.

Simplest approach: keep the sessionId column in the table but derive it from the turnId. Add a lookup in record():
```kotlin
override suspend fun record(turnId: TurnId, details: TokenDetails) {
    transaction(db = database) {
        // sessionId can be looked up from TurnsTable if needed for getBySession queries
        // For now, store with the turn reference
        TokensTable.insert {
            it[TokensTable.turnId] = turnId.value
            it[promptTokens] = details.promptTokens
            // ... etc
        }
    }
}
```

Note: `getBySession` still needs to work. If the table has a sessionId column, you'll need to populate it. Two options:
1. Join with TurnsTable to get sessionId
2. Keep sessionId in the table — require the implementation to look it up

Choose option 1 (join) to keep the interface clean. Update `getBySession` to join on TurnsTable.

Same pattern for `ExposedCostRepository`.

- [ ] **Step 4: Update AiAgent.kt**

Lines 90-91:
```kotlin
tokenRepository.record(turnId = turnId, details = tokenDetails)
costRepository.record(turnId = turnId, details = costDetails)
```

- [ ] **Step 5: Update tests**

Update FakeTokenRepository and FakeCostRepository in test files — remove sessionId from record().
Update ExposedTokenRepositoryTest and ExposedCostRepositoryTest.

- [ ] **Step 6: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: remove sessionId from TokenDetailsRepository.record and CostDetailsRepository.record"
```

---

### Task 7: Repository accepts full entity

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/session/AgentSessionRepository.kt`
- Modify: `modules/data/session-repository-exposed/src/main/kotlin/.../ExposedSessionRepository.kt`
- Modify: `modules/domain/ai-agent/src/main/kotlin/.../AiAgent.kt`
- Modify: test files

- [ ] **Step 1: Update AgentSessionRepository interface**

```kotlin
package com.ai.challenge.core.session

interface AgentSessionRepository {
    suspend fun save(session: AgentSession): AgentSessionId
    suspend fun get(id: AgentSessionId): AgentSession?
    suspend fun delete(id: AgentSessionId): Boolean
    suspend fun list(): List<AgentSession>
    suspend fun update(session: AgentSession)
}
```

- [ ] **Step 2: Update ExposedSessionRepository**

Replace `create(title)` with `save(session)`:
```kotlin
override suspend fun save(session: AgentSession): AgentSessionId {
    transaction(db = database) {
        SessionsTable.insert {
            it[id] = session.id.value
            it[title] = session.title
            it[createdAt] = session.createdAt.toString()
            it[updatedAt] = session.updatedAt.toString()
        }
    }
    return session.id
}
```

Replace `updateTitle(id, title)` with `update(session)`:
```kotlin
override suspend fun update(session: AgentSession) {
    transaction(db = database) {
        SessionsTable.update({ SessionsTable.id eq session.id.value }) {
            it[title] = session.title
            it[updatedAt] = session.updatedAt.toString()
        }
    }
}
```

- [ ] **Step 3: Update AiAgent.kt**

`createSession`:
```kotlin
override suspend fun createSession(title: String): AgentSessionId {
    val session = AgentSession(
        id = AgentSessionId.generate(),
        title = title,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
    )
    val sessionId = sessionRepository.save(session = session)
    contextManagementRepository.save(sessionId = sessionId, type = ContextManagementType.None)
    return sessionId
}
```

`updateSessionTitle`:
```kotlin
override suspend fun updateSessionTitle(id: AgentSessionId, title: String) {
    val session = sessionRepository.get(id = id) ?: return
    sessionRepository.update(session = session.copy(title = title, updatedAt = Clock.System.now()))
}
```

- [ ] **Step 4: Update tests**

Update FakeSessionRepository in all test files — replace `create` with `save`, `updateTitle` with `update`.

- [ ] **Step 5: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: AgentSessionRepository accepts full entity (save/update instead of create/updateTitle)"
```

---

### Task 8: Enrich AgentSession with behavior

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/session/AgentSession.kt`
- Modify: `modules/domain/ai-agent/src/main/kotlin/.../AiAgent.kt`

- [ ] **Step 1: Add factory method and behavior to AgentSession**

```kotlin
package com.ai.challenge.core.session

import kotlin.time.Clock
import kotlin.time.Instant

data class AgentSession(
    val id: AgentSessionId,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun withUpdatedTitle(newTitle: String): AgentSession =
        copy(title = newTitle, updatedAt = Clock.System.now())

    companion object {
        fun create(title: String): AgentSession {
            val now = Clock.System.now()
            return AgentSession(
                id = AgentSessionId.generate(),
                title = title,
                createdAt = now,
                updatedAt = now,
            )
        }
    }
}
```

- [ ] **Step 2: Update AiAgent.kt to use factory and behavior**

`createSession`:
```kotlin
override suspend fun createSession(title: String): AgentSessionId {
    val session = AgentSession.create(title = title)
    val sessionId = sessionRepository.save(session = session)
    contextManagementRepository.save(sessionId = sessionId, type = ContextManagementType.None)
    return sessionId
}
```

`updateSessionTitle`:
```kotlin
override suspend fun updateSessionTitle(id: AgentSessionId, title: String) {
    val session = sessionRepository.get(id = id) ?: return
    sessionRepository.update(session = session.withUpdatedTitle(newTitle = title))
}
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: enrich AgentSession with create() factory and withUpdatedTitle() method"
```

---

### Task 9: Unify error handling to Either

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/agent/Agent.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/agent/AgentError.kt`
- Modify: `modules/domain/ai-agent/src/main/kotlin/.../AiAgent.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/.../ui/chat/store/ChatStoreFactory.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/.../ui/sessionlist/store/SessionListStoreFactory.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/.../ui/root/RootComponent.kt`
- Modify: all test files using Agent

- [ ] **Step 1: Add new error types to AgentError**

```kotlin
package com.ai.challenge.core.agent

sealed interface AgentError {
    val message: String

    data class NetworkError(override val message: String) : AgentError
    data class ApiError(override val message: String) : AgentError
    data class NotFound(override val message: String) : AgentError
    data class DatabaseError(override val message: String) : AgentError
}
```

- [ ] **Step 2: Update Agent interface — all methods return Either**

```kotlin
interface Agent {
    suspend fun send(sessionId: AgentSessionId, message: String): Either<AgentError, AgentResponse>
    suspend fun createSession(title: String): Either<AgentError, AgentSessionId>
    suspend fun deleteSession(id: AgentSessionId): Either<AgentError, Unit>
    suspend fun listSessions(): Either<AgentError, List<AgentSession>>
    suspend fun getSession(id: AgentSessionId): Either<AgentError, AgentSession>
    suspend fun updateSessionTitle(id: AgentSessionId, title: String): Either<AgentError, Unit>
    suspend fun getTurns(sessionId: AgentSessionId, limit: Int?): Either<AgentError, List<Turn>>
    suspend fun getTokensByTurn(turnId: TurnId): Either<AgentError, TokenDetails>
    suspend fun getTokensBySession(sessionId: AgentSessionId): Either<AgentError, Map<TurnId, TokenDetails>>
    suspend fun getSessionTotalTokens(sessionId: AgentSessionId): Either<AgentError, TokenDetails>
    suspend fun getCostByTurn(turnId: TurnId): Either<AgentError, CostDetails>
    suspend fun getCostBySession(sessionId: AgentSessionId): Either<AgentError, Map<TurnId, CostDetails>>
    suspend fun getSessionTotalCost(sessionId: AgentSessionId): Either<AgentError, CostDetails>
    suspend fun getContextManagementType(sessionId: AgentSessionId): Either<AgentError, ContextManagementType>
    suspend fun updateContextManagementType(sessionId: AgentSessionId, type: ContextManagementType): Either<AgentError, Unit>
    suspend fun createBranch(sessionId: AgentSessionId, name: String, parentTurnId: TurnId, fromBranchId: BranchId): Either<AgentError, BranchId>
    suspend fun deleteBranch(branchId: BranchId): Either<AgentError, Unit>
    suspend fun getBranches(sessionId: AgentSessionId): Either<AgentError, List<Branch>>
    suspend fun switchBranch(sessionId: AgentSessionId, branchId: BranchId): Either<AgentError, Unit>
    suspend fun getActiveBranch(sessionId: AgentSessionId): Either<AgentError, Branch?>
    suspend fun getActiveBranchTurns(sessionId: AgentSessionId): Either<AgentError, List<Turn>>
    suspend fun getBranchParentMap(sessionId: AgentSessionId): Either<AgentError, Map<BranchId, BranchId?>>
}
```

- [ ] **Step 3: Update AiAgent implementations**

Wrap previously non-Either methods in `Either.Right` or `either { }`:

```kotlin
override suspend fun createSession(title: String): Either<AgentError, AgentSessionId> = either {
    val session = AgentSession.create(title = title)
    val sessionId = sessionRepository.save(session = session)
    contextManagementRepository.save(sessionId = sessionId, type = ContextManagementType.None)
    sessionId
}

override suspend fun deleteSession(id: AgentSessionId): Either<AgentError, Unit> = either {
    contextManagementRepository.delete(id)
    sessionRepository.delete(id)
}

override suspend fun listSessions(): Either<AgentError, List<AgentSession>> =
    Either.Right(value = sessionRepository.list())

override suspend fun getSession(id: AgentSessionId): Either<AgentError, AgentSession> = either {
    sessionRepository.get(id = id) ?: raise(AgentError.NotFound(message = "Session not found: ${id.value}"))
}

override suspend fun updateSessionTitle(id: AgentSessionId, title: String): Either<AgentError, Unit> = either {
    val session = sessionRepository.get(id = id) ?: raise(AgentError.NotFound(message = "Session not found: ${id.value}"))
    sessionRepository.update(session = session.withUpdatedTitle(newTitle = title))
}

override suspend fun getTurns(sessionId: AgentSessionId, limit: Int?): Either<AgentError, List<Turn>> =
    Either.Right(value = turnRepository.getBySession(sessionId = sessionId, limit = limit))

override suspend fun getTokensByTurn(turnId: TurnId): Either<AgentError, TokenDetails> = either {
    tokenRepository.getByTurn(turnId = turnId) ?: raise(AgentError.NotFound(message = "Token details not found for turn: ${turnId.value}"))
}

override suspend fun getTokensBySession(sessionId: AgentSessionId): Either<AgentError, Map<TurnId, TokenDetails>> =
    Either.Right(value = tokenRepository.getBySession(sessionId = sessionId))

override suspend fun getSessionTotalTokens(sessionId: AgentSessionId): Either<AgentError, TokenDetails> =
    Either.Right(value = tokenRepository.getSessionTotal(sessionId = sessionId))

override suspend fun getCostByTurn(turnId: TurnId): Either<AgentError, CostDetails> = either {
    costRepository.getByTurn(turnId = turnId) ?: raise(AgentError.NotFound(message = "Cost details not found for turn: ${turnId.value}"))
}

override suspend fun getCostBySession(sessionId: AgentSessionId): Either<AgentError, Map<TurnId, CostDetails>> =
    Either.Right(value = costRepository.getBySession(sessionId = sessionId))

override suspend fun getSessionTotalCost(sessionId: AgentSessionId): Either<AgentError, CostDetails> =
    Either.Right(value = costRepository.getSessionTotal(sessionId = sessionId))
```

- [ ] **Step 4: Update SessionListStoreFactory.kt**

`handleLoadSessions`:
```kotlin
private fun handleLoadSessions() {
    scope.launch {
        when (val result = agent.listSessions()) {
            is Either.Right -> {
                val sessions = result.value.map { session ->
                    SessionListStore.SessionItem(
                        id = session.id,
                        title = session.title,
                        updatedAt = session.updatedAt,
                    )
                }
                dispatch(Msg.SessionsLoaded(sessions = sessions, activeSessionId = state().activeSessionId))
            }
            is Either.Left -> {} // silently ignore for now
        }
    }
}
```

`handleCreateSession`:
```kotlin
private fun handleCreateSession() {
    scope.launch {
        when (val result = agent.createSession(title = "")) {
            is Either.Right -> {
                when (val sessionResult = agent.getSession(id = result.value)) {
                    is Either.Right -> {
                        val item = SessionListStore.SessionItem(
                            id = sessionResult.value.id,
                            title = sessionResult.value.title,
                            updatedAt = sessionResult.value.updatedAt,
                        )
                        dispatch(Msg.SessionCreated(item = item))
                    }
                    is Either.Left -> {}
                }
            }
            is Either.Left -> {}
        }
    }
}
```

`handleDeleteSession`:
```kotlin
private fun handleDeleteSession(id: AgentSessionId) {
    scope.launch {
        agent.deleteSession(id = id)
        when (val result = agent.listSessions()) {
            is Either.Right -> {
                val currentActive = state().activeSessionId
                val newActiveId = if (currentActive == id) {
                    result.value.firstOrNull()?.id
                } else {
                    currentActive
                }
                dispatch(Msg.SessionDeleted(id = id, newActiveId = newActiveId))
            }
            is Either.Left -> {}
        }
    }
}
```

- [ ] **Step 5: Update ChatStoreFactory.kt**

`handleLoadSession` — wrap `agent.getTurns`, `agent.getTokensBySession`, `agent.getCostBySession` in Either handling:
```kotlin
val history = when (val r = agent.getActiveBranchTurns(sessionId = sessionId)) {
    is Either.Right -> r.value
    is Either.Left -> when (val t = agent.getTurns(sessionId = sessionId, limit = null)) {
        is Either.Right -> t.value
        is Either.Left -> emptyList()
    }
}
val turnTokens = when (val r = agent.getTokensBySession(sessionId = sessionId)) {
    is Either.Right -> r.value
    is Either.Left -> emptyMap()
}
val turnCosts = when (val r = agent.getCostBySession(sessionId = sessionId)) {
    is Either.Right -> r.value
    is Either.Left -> emptyMap()
}
```

`handleSendMessage` — `agent.getSession` and `agent.updateSessionTitle` now return Either:
```kotlin
when (val sessionResult = agent.getSession(id = sessionId)) {
    is Either.Right -> {
        if (sessionResult.value.title.isEmpty()) {
            agent.updateSessionTitle(id = sessionId, title = text.take(n = 50))
        }
    }
    is Either.Left -> {}
}
```

Same pattern for `handleSwitchBranch` — wrap token/cost calls.

- [ ] **Step 6: Update RootComponent.kt**

`init` block, `createNewSession`, `deleteSession` — all calls to `agent.createSession()`, `agent.listSessions()`, `agent.deleteSession()`, `agent.getSession()` now return Either. Wrap in `when`:

```kotlin
init {
    runBlocking {
        val sessions = when (val r = agent.listSessions()) {
            is Either.Right -> r.value
            is Either.Left -> emptyList()
        }
        if (sessions.isEmpty()) {
            when (val r = agent.createSession(title = "")) {
                is Either.Right -> {
                    sessionListStore.accept(SessionListStore.Intent.LoadSessions)
                    selectSession(r.value)
                }
                is Either.Left -> {}
            }
        } else {
            sessionListStore.accept(SessionListStore.Intent.LoadSessions)
            selectSession(sessions.first().id)
        }
    }
}
```

- [ ] **Step 7: Update all test files**

Update FakeAgent implementations in ChatStoreTest and SessionListStoreTest to return Either from all methods.
Update OpenRouterAgentTest assertions for new return types.

- [ ] **Step 8: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor: unify all Agent methods to return Either<AgentError, T>"
```

---

### Task 10: Split Agent into 4 interfaces

**Files:**
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/agent/ChatAgent.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/agent/SessionManager.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/agent/UsageTracker.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/agent/BranchManager.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/agent/Agent.kt`
- Modify: `modules/domain/ai-agent/src/main/kotlin/.../AiAgent.kt`
- Modify: `modules/presentation/app/src/main/kotlin/.../di/AppModule.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/.../ui/root/RootComponent.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/.../ui/chat/ChatComponent.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/.../ui/chat/store/ChatStoreFactory.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/.../ui/sessionlist/store/SessionListStoreFactory.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/.../ui/settings/SessionSettingsComponent.kt`
- Modify: all test files

- [ ] **Step 1: Create ChatAgent interface**

```kotlin
package com.ai.challenge.core.agent

import arrow.core.Either
import com.ai.challenge.core.session.AgentSessionId

interface ChatAgent {
    suspend fun send(sessionId: AgentSessionId, message: String): Either<AgentError, AgentResponse>
}
```

- [ ] **Step 2: Create SessionManager interface**

```kotlin
package com.ai.challenge.core.agent

import arrow.core.Either
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId

interface SessionManager {
    suspend fun createSession(title: String): Either<AgentError, AgentSessionId>
    suspend fun deleteSession(id: AgentSessionId): Either<AgentError, Unit>
    suspend fun listSessions(): Either<AgentError, List<AgentSession>>
    suspend fun getSession(id: AgentSessionId): Either<AgentError, AgentSession>
    suspend fun updateSessionTitle(id: AgentSessionId, title: String): Either<AgentError, Unit>
    suspend fun getTurns(sessionId: AgentSessionId, limit: Int?): Either<AgentError, List<Turn>>
    suspend fun getContextManagementType(sessionId: AgentSessionId): Either<AgentError, ContextManagementType>
    suspend fun updateContextManagementType(sessionId: AgentSessionId, type: ContextManagementType): Either<AgentError, Unit>
}
```

- [ ] **Step 3: Create UsageTracker interface**

```kotlin
package com.ai.challenge.core.agent

import arrow.core.Either
import com.ai.challenge.core.cost.CostDetails
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.token.TokenDetails
import com.ai.challenge.core.turn.TurnId

interface UsageTracker {
    suspend fun getTokensByTurn(turnId: TurnId): Either<AgentError, TokenDetails>
    suspend fun getTokensBySession(sessionId: AgentSessionId): Either<AgentError, Map<TurnId, TokenDetails>>
    suspend fun getSessionTotalTokens(sessionId: AgentSessionId): Either<AgentError, TokenDetails>
    suspend fun getCostByTurn(turnId: TurnId): Either<AgentError, CostDetails>
    suspend fun getCostBySession(sessionId: AgentSessionId): Either<AgentError, Map<TurnId, CostDetails>>
    suspend fun getSessionTotalCost(sessionId: AgentSessionId): Either<AgentError, CostDetails>
}
```

- [ ] **Step 4: Create BranchManager interface**

```kotlin
package com.ai.challenge.core.agent

import arrow.core.Either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId

interface BranchManager {
    suspend fun createBranch(sessionId: AgentSessionId, name: String, parentTurnId: TurnId, fromBranchId: BranchId): Either<AgentError, BranchId>
    suspend fun deleteBranch(branchId: BranchId): Either<AgentError, Unit>
    suspend fun getBranches(sessionId: AgentSessionId): Either<AgentError, List<Branch>>
    suspend fun switchBranch(sessionId: AgentSessionId, branchId: BranchId): Either<AgentError, Unit>
    suspend fun getActiveBranch(sessionId: AgentSessionId): Either<AgentError, Branch?>
    suspend fun getActiveBranchTurns(sessionId: AgentSessionId): Either<AgentError, List<Turn>>
    suspend fun getBranchParentMap(sessionId: AgentSessionId): Either<AgentError, Map<BranchId, BranchId?>>
}
```

- [ ] **Step 5: Update Agent to extend all four**

```kotlin
package com.ai.challenge.core.agent

interface Agent : ChatAgent, SessionManager, UsageTracker, BranchManager
```

- [ ] **Step 6: Update AiAgent to implement Agent (which extends all four)**

```kotlin
class AiAgent(
    // ... same constructor
) : Agent {
    // ... all implementations unchanged
}
```

No change needed in method implementations — AiAgent already implements everything.

- [ ] **Step 7: Update AppModule.kt — bind each interface**

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
    )
}
single<ChatAgent> { get<Agent>() }
single<SessionManager> { get<Agent>() }
single<UsageTracker> { get<Agent>() }
single<BranchManager> { get<Agent>() }
```

- [ ] **Step 8: Update RootComponent to use specific interfaces**

```kotlin
class RootComponent(
    componentContext: ComponentContext,
    private val storeFactory: StoreFactory,
    private val sessionManager: SessionManager,
    private val chatAgent: ChatAgent,
    private val usageTracker: UsageTracker,
    private val branchManager: BranchManager,
) : ComponentContext by componentContext {
```

Replace `agent.listSessions()` with `sessionManager.listSessions()`, `agent.createSession()` with `sessionManager.createSession()`, etc.

Pass specific interfaces to child components:
```kotlin
is Config.Chat -> Child.Chat(
    ChatComponent(
        componentContext = componentContext,
        storeFactory = storeFactory,
        chatAgent = chatAgent,
        sessionManager = sessionManager,
        usageTracker = usageTracker,
        branchManager = branchManager,
        sessionId = AgentSessionId(value = config.sessionId),
    )
)
```

SessionListStoreFactory now takes `SessionManager`:
```kotlin
SessionListStoreFactory(storeFactory = storeFactory, sessionManager = sessionManager).create()
```

- [ ] **Step 9: Update ChatComponent and ChatStoreFactory**

`ChatComponent` constructor takes `ChatAgent`, `SessionManager`, `UsageTracker`, `BranchManager` instead of `Agent`.

`ChatStoreFactory` constructor:
```kotlin
class ChatStoreFactory(
    private val storeFactory: StoreFactory,
    private val chatAgent: ChatAgent,
    private val sessionManager: SessionManager,
    private val usageTracker: UsageTracker,
    private val branchManager: BranchManager,
) {
```

`ExecutorImpl` takes the same 4 interfaces. Update all method calls:
- `agent.send(...)` → `chatAgent.send(...)`
- `agent.getTurns(...)` → `sessionManager.getTurns(...)`
- `agent.getSession(...)` → `sessionManager.getSession(...)`
- `agent.updateSessionTitle(...)` → `sessionManager.updateSessionTitle(...)`
- `agent.getTokensBySession(...)` → `usageTracker.getTokensBySession(...)`
- `agent.getCostBySession(...)` → `usageTracker.getCostBySession(...)`
- `agent.getBranches(...)` → `branchManager.getBranches(...)`
- `agent.getActiveBranch(...)` → `branchManager.getActiveBranch(...)`
- `agent.getActiveBranchTurns(...)` → `branchManager.getActiveBranchTurns(...)`
- `agent.createBranch(...)` → `branchManager.createBranch(...)`
- `agent.switchBranch(...)` → `branchManager.switchBranch(...)`
- `agent.deleteBranch(...)` → `branchManager.deleteBranch(...)`
- `agent.getContextManagementType(...)` → `sessionManager.getContextManagementType(...)`
- `agent.getBranchParentMap(...)` → `branchManager.getBranchParentMap(...)`

- [ ] **Step 10: Update SessionListStoreFactory**

```kotlin
class SessionListStoreFactory(
    private val storeFactory: StoreFactory,
    private val sessionManager: SessionManager,
) {
```

Replace all `agent.` calls with `sessionManager.`:
- `agent.listSessions()` → `sessionManager.listSessions()`
- `agent.createSession(...)` → `sessionManager.createSession(...)`
- `agent.getSession(...)` → `sessionManager.getSession(...)`
- `agent.deleteSession(...)` → `sessionManager.deleteSession(...)`

- [ ] **Step 11: Update SessionSettingsComponent**

Takes `SessionManager` instead of `Agent`. The store factory for settings uses `sessionManager.getContextManagementType()` and `sessionManager.updateContextManagementType()`.

- [ ] **Step 12: Update Main.kt**

```kotlin
val agent = koin.get<Agent>()
val root = runOnUiThread {
    RootComponent(
        componentContext = DefaultComponentContext(lifecycle = lifecycle),
        storeFactory = DefaultStoreFactory(),
        sessionManager = agent,
        chatAgent = agent,
        usageTracker = agent,
        branchManager = agent,
    )
}
```

Or alternatively, get each interface from Koin:
```kotlin
RootComponent(
    componentContext = DefaultComponentContext(lifecycle = lifecycle),
    storeFactory = DefaultStoreFactory(),
    sessionManager = koin.get<SessionManager>(),
    chatAgent = koin.get<ChatAgent>(),
    usageTracker = koin.get<UsageTracker>(),
    branchManager = koin.get<BranchManager>(),
)
```

- [ ] **Step 13: Update all test files**

Update FakeAgent in test files to implement `Agent` (which extends all 4 interfaces).
Update test factories to pass specific interfaces instead of Agent.

- [ ] **Step 14: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 15: Commit**

```bash
git add -A
git commit -m "refactor: split Agent into ChatAgent, SessionManager, UsageTracker, BranchManager interfaces"
```
