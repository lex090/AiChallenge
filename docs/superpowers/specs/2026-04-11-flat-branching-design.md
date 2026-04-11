# Flat Branching Model — Design Spec

## Problem

Current branching model uses a tree hierarchy (`Branch.parentId -> Branch`) with `activeBranchId` stored in the `AgentSession` aggregate on the backend. In practice, branches are simply alternative chats from a specific Turn within a session — there is no meaningful inheritance between branches. The tree structure adds complexity (cascade deletion, parent maps, hierarchical UI rendering) without delivering value.

Additionally, `AgentSession` is declared as an aggregate root but does not protect its invariants — branch validation logic lives in `AiBranchService`, making the aggregate anemic.

## Goals

1. Replace tree branching (`Branch -> Branch`) with flat branching (`Branch -> Turn`)
2. Move `activeBranchId` from backend (`AgentSession`) to frontend (`ChatStore.State`)
3. Review and fix aggregate/entity boundaries following DDD principles
4. Simplify `BranchService` API — remove hierarchy-related methods
5. Make `branchId` an explicit parameter in `ChatService.send()` and `ContextManager.prepareContext()`

## Non-Goals

- Changing Turn structure or UsageRecord
- Modifying context management strategies other than `BranchingContextManager`
- Changing the repository technology (Exposed/SQLite)

---

## Design

### Aggregate Boundaries

The current model declares `AgentSession` as the single aggregate root with `Branch` and `Turn` as child entities. DDD analysis reveals this is incorrect:

**`AgentSession`** — has no knowledge of its branches (no `branches: List<Branch>`), cannot enforce branch invariants. Loading all branches for every session operation (update title, change context type) is wasteful.

**`Branch`** — has its own identity (`BranchId`), its own lifecycle (create/delete independently), and self-sufficient invariants (`isMain` check on delete). Contains `TurnSequence` as its consistency boundary.

**Decision: Branch is promoted to a separate aggregate root.**

| Element | DDD Role | Rationale |
|---------|----------|-----------|
| `AgentSession` | Aggregate Root | Session-level data only (title, contextType, timestamps). No branch references. |
| `Branch` | Aggregate Root | Own lifecycle, self-enforced invariants, `TurnSequence` as consistency boundary. |
| `Turn` | Shared Entity | Immutable, shared across branches via join table, referenced by ID. |
| `TurnSequence` | Value Object | Ordered `List<TurnId>` with trunk operation. Encapsulates domain logic. |
| `BranchService` | Domain Service | Coordinates between aggregates (reads source branch to create new branch). |

### Domain Models (core)

#### AgentSession

```kotlin
data class AgentSession(
    val id: AgentSessionId,
    val title: SessionTitle,
    val contextManagementType: ContextManagementType,
    val createdAt: CreatedAt,
    val updatedAt: UpdatedAt,
)
```

Removed:
- `activeBranchId: BranchId` — moves to frontend `ChatStore.State`
- `withActiveBranch()` — deleted

Retained:
- `withUpdatedTitle()`
- `withContextManagementType()`

#### Branch

```kotlin
data class Branch(
    val id: BranchId,
    val sessionId: AgentSessionId,
    val sourceTurnId: TurnId?,       // null = main branch
    val turnSequence: TurnSequence,
    val createdAt: CreatedAt,
) {
    val isMain: Boolean get() = sourceTurnId == null

    fun ensureDeletable(): Either<DomainError, Unit> =
        if (isMain) Either.Left(DomainError.MainBranchCannotBeDeleted(sessionId = sessionId))
        else Either.Right(Unit)
}
```

Removed:
- `parentId: BranchId?` — no branch-to-branch hierarchy
- `name: BranchName` — auto-generated on UI side

Added:
- `sourceTurnId: TurnId?` — the Turn from which this branch diverges; null means main
- `turnSequence: TurnSequence` — replaces `turnIds: List<TurnId>`
- `isMain` — computed property (sourceTurnId == null), same semantics as before
- `ensureDeletable()` — aggregate-level invariant protection

#### TurnSequence (new Value Object)

```kotlin
@JvmInline
value class TurnSequence(val values: List<TurnId>) {
    fun trunkUpTo(turnId: TurnId): TurnSequence {
        val index = values.indexOf(element = turnId)
        require(index >= 0) { "Turn $turnId not found in sequence" }
        return TurnSequence(values = values.subList(fromIndex = 0, toIndex = index + 1))
    }
}
```

Encapsulates the ordered turn list and the trunk-copy operation that was previously in `AiBranchService`.

#### Turn — no changes

Remains as-is: immutable shared entity with `TurnId`, `sessionId`, messages, `UsageRecord`, `CreatedAt`.

#### BranchName — deleted

Value object removed entirely. Branch display name is generated on the UI side.

### DomainError — changes

Retained:
- `BranchNotFound`, `MainBranchCannotBeDeleted`, `BranchingNotEnabled`, `BranchNotOwnedBySession`
- `SessionNotFound`, `TurnNotFound`, `NetworkError`, `ApiError`, `DatabaseError`

No new error types needed.

### Service Interfaces (core)

#### BranchService

```kotlin
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

Removed methods:
- `switch()` — active branch is now frontend state
- `getActive()` — no active branch on backend
- `getActiveTurns()` — replaced by `getTurns(branchId)`
- `getParentMap()` — no hierarchy

#### ChatService

```kotlin
interface ChatService {
    suspend fun send(
        sessionId: AgentSessionId,
        branchId: BranchId,
        message: MessageContent,
    ): Either<DomainError, Turn>
}
```

`branchId` is now an explicit parameter — the backend does not decide which branch to write to.

#### SessionService — no changes

Retained as-is. `create()` still creates a main branch, but no longer sets `activeBranchId` on the session.

#### ContextManager

```kotlin
interface ContextManager {
    suspend fun prepareContext(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
    ): PreparedContext
}
```

`branchId` is explicit — `BranchingContextManager` no longer reads `session.activeBranchId`.

### Service Implementations (domain/ai-agent)

#### AiBranchService

```kotlin
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

    override suspend fun getAll(sessionId: AgentSessionId): Either<DomainError, List<Branch>>
        // Delegates to repository.getBranches(sessionId)

    override suspend fun getTurns(branchId: BranchId): Either<DomainError, List<Turn>>
        // Delegates to repository.getTurnsByBranch(branchId)
}
```

Removed: `switch()`, `getActive()`, `getActiveTurns()`, `getParentMap()`, `cascadeDeleteBranch()`.

#### AiChatService

`send()` accepts `branchId` explicitly, passes it to `contextManager.prepareContext()` and `repository.appendTurn()`. No conditional logic for "if branching enabled, use activeBranch".

#### AiSessionService

`create()` builds `AgentSession` without `activeBranchId`. Still creates a main `Branch(sourceTurnId = null)`.

`updateContextManagementType()` — when switching to Branching mode, the existing turns must already be in the main branch (they are appended there by default). No migration logic is needed — just verify that the main branch exists and contains all session turns. If the main branch is missing (edge case), create it and link existing turns.

#### BranchingContextManager

```kotlin
override suspend fun prepareContext(
    sessionId: AgentSessionId,
    branchId: BranchId,
    newMessage: MessageContent,
): PreparedContext {
    val turns = repository.getTurnsByBranch(branchId = branchId)
    // Build context from branch turns only — branchId is explicit
}
```

No longer reads `session.activeBranchId`.

### Repository (core interface + data implementation)

#### AgentSessionRepository

```kotlin
interface AgentSessionRepository {
    // Session lifecycle — unchanged
    suspend fun save(session: AgentSession): AgentSession
    suspend fun get(id: AgentSessionId): AgentSession?
    suspend fun delete(id: AgentSessionId)
    suspend fun list(): List<AgentSession>
    suspend fun update(session: AgentSession): AgentSession

    // Branches
    suspend fun createBranch(branch: Branch): Branch
    suspend fun getBranches(sessionId: AgentSessionId): List<Branch>
    suspend fun getBranch(branchId: BranchId): Branch?
    suspend fun getMainBranch(sessionId: AgentSessionId): Branch?
    suspend fun deleteBranch(branchId: BranchId)
    suspend fun deleteTurnsByBranch(branchId: BranchId)

    // Turns
    suspend fun appendTurn(branchId: BranchId, turn: Turn): Turn
    suspend fun getTurnsByBranch(branchId: BranchId): List<Turn>
    suspend fun getTurn(turnId: TurnId): Turn?
}
```

Removed: `getTurns(sessionId, limit)` — no reading turns without a branch context.

#### DB Schema Changes

**SessionsTable:**
- Remove column `active_branch_id`

**BranchesTable:**
- Remove column `parent_id`
- Remove column `name`
- Add column `source_turn_id: varchar(36) nullable`

**BranchTurnsTable:** — no changes
**TurnsTable:** — no changes

#### getMainBranch implementation

Changes from `WHERE parent_id IS NULL` to `WHERE source_turn_id IS NULL`.

### Presentation Layer (compose-ui)

#### ChatStore.State

```kotlin
data class State(
    val sessionId: AgentSessionId?,
    val messages: List<UiMessage>,
    val branches: List<Branch>,
    val activeBranchId: BranchId?,    // local UI state, not persisted on backend
    val isBranchingEnabled: Boolean,
    // other fields unchanged
)
```

Removed: `activeBranch: Branch?`, `branchParentMap: Map<BranchId, BranchId?>`.

#### Intents

```kotlin
sealed interface Intent {
    data class CreateBranch(val sourceTurnId: TurnId) : Intent
    data class SwitchBranch(val branchId: BranchId) : Intent
    data class DeleteBranch(val branchId: BranchId) : Intent
    data class SendMessage(val text: String) : Intent
    data object LoadBranches : Intent
}
```

`CreateBranch` no longer takes `name`. Uses `state.activeBranchId` as `fromBranchId`.

#### Executor Logic

**CreateBranch:**
1. Call `branchService.create(sessionId, sourceTurnId, fromBranchId = state.activeBranchId)`
2. Update `branches` in state
3. Auto-switch `activeBranchId` to new branch
4. Load turns for new branch

**SwitchBranch:**
1. Update `activeBranchId` in local state (no backend call)
2. Call `branchService.getTurns(branchId)` to load messages

**DeleteBranch:**
1. Call `branchService.delete(branchId)`
2. If deleted branch was active, switch to main branch
3. Update `branches` in state

**SendMessage:**
1. Call `chatService.send(sessionId, branchId = state.activeBranchId, message)`
2. Append Turn to messages

#### BranchPanel UI

Tree rendering (`BranchTreeNode` with recursive `parentId` traversal) replaced with flat list. Branches can be grouped by `sourceTurnId` to show "N alternative continuations from this message".

---

## Summary of Deletions

| What | Where |
|------|-------|
| `activeBranchId` field | `AgentSession` model |
| `withActiveBranch()` method | `AgentSession` model |
| `parentId` field | `Branch` model |
| `name` field | `Branch` model |
| `BranchName` value object | `core/chat/model/` |
| `active_branch_id` column | `SessionsTable` |
| `parent_id` column | `BranchesTable` |
| `name` column | `BranchesTable` |
| `switch()` method | `BranchService` interface + implementation |
| `getActive()` method | `BranchService` interface + implementation |
| `getActiveTurns()` method | `BranchService` interface + implementation |
| `getParentMap()` method | `BranchService` interface + implementation |
| `cascadeDeleteBranch()` | `AiBranchService` implementation |
| `getTurns(sessionId, limit)` | `AgentSessionRepository` |
| `activeBranch: Branch?` | `ChatStore.State` |
| `branchParentMap` | `ChatStore.State` |
| `BranchTreeNode` composable | `ChatContent.kt` |

## Summary of Additions

| What | Where |
|------|-------|
| `sourceTurnId: TurnId?` field | `Branch` model |
| `turnSequence: TurnSequence` field | `Branch` model (replaces `turnIds`) |
| `ensureDeletable()` method | `Branch` model |
| `TurnSequence` value object | `core/branch/` (new file) |
| `source_turn_id` column | `BranchesTable` |
| `activeBranchId: BranchId?` | `ChatStore.State` |
| `branchId` parameter | `ChatService.send()`, `ContextManager.prepareContext()` |
| `getTurns(branchId)` method | `BranchService` interface |
