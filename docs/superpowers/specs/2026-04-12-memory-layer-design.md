# Memory Layer Design

## Summary

Introduce a Memory Layer as a dedicated Bounded Context with a registry-based `MemoryService` facade. Migrates existing `Fact` and `Summary` from scattered packages into a unified `core/memory` domain, adds a `MemoryProvider` registry pattern for extensible memory types, consolidates two databases into one (`memory.db`), and adds a debug panel for observing and editing memory at runtime.

## Motivation

Currently `Fact` and `Summary` are Value Objects with independent packages (`core/fact/`, `core/summary/`), separate databases (`facts.db`, `summaries.db`), and separate repository implementations. Context manager strategies access repositories directly. This works but doesn't scale:

- Adding a new memory type (e.g., user-level preferences, embeddings) requires new repositories, new cleanup handlers, new DI wiring, with no unifying abstraction.
- No way to query "all memory for a session" — each type accessed independently.
- Cleanup handler (`SessionDeletedCleanupHandler`) must know about every repository explicitly.
- No UI visibility into agent memory state.

## Design Decisions

### DD1: Facade with Registry Pattern (not unified model, not thin wrapper)

**Decision:** `MemoryService` as a facade with `MemoryProvider` registry. Each memory type keeps its own VO model and write semantics.

**Why not unified model:** Fact (replace-all: category+key+value) and Summary (append-only: content+turnRange) have fundamentally different semantics and structures. Merging into `MemoryEntry` violates E3 (Value Object identity) and loses type safety.

**Why not thin wrapper:** A wrapper that just delegates to repositories adds no value. The registry pattern adds: type-safe lookup, automatic cleanup via `clearScope()`, open/closed extensibility.

**Rules:** E3 (preserve VO semantics), E6 (domain service), V2 (small and focused providers).

### DD2: MemoryScope as Value Object

**Decision:** `MemoryScope` sealed interface in core. Currently: `Session(sessionId)`. Future: `User(userId)`, `Branch(branchId)`.

**Why:** Scope is a first-class domain concept — it determines the boundary within which memory lives. Modeling it explicitly enables future multi-scope support without changing existing providers.

**Rules:** E3 (immutable, equality by attributes), E1 (BC vocabulary).

### DD3: Phantom Type on MemoryType for type-safe registry

**Decision:** `MemoryType<P : MemoryProvider<*>>` where the generic parameter carries the concrete provider type. `MemoryService.provider(type)` returns `P` directly.

```kotlin
sealed interface MemoryType<P : MemoryProvider<*>> {
    data object Facts : MemoryType<FactMemoryProvider>
    data object Summaries : MemoryType<SummaryMemoryProvider>
}

interface MemoryService {
    fun <P : MemoryProvider<*>> provider(type: MemoryType<P>): P
    suspend fun clearScope(scope: MemoryScope)
}
```

**Why:** Full type safety at call sites — no casts in consumers. Single `@Suppress("UNCHECKED_CAST")` hidden inside registry implementation (heterogeneous map).

**Alternatives considered:**
- Named accessors (`val facts`, `val summaries`) — fully typed but breaks open/closed.
- Cast at call site — open/closed but fragile, not caught by compiler.

### DD4: No save in base MemoryProvider interface

**Decision:** `MemoryProvider<T>` has only `get(scope)` and `clear(scope)`. Write operations are on specific sub-interfaces: `FactMemoryProvider.replace()`, `SummaryMemoryProvider.append()`, `SummaryMemoryProvider.delete()`.

**Why:** Replace-all vs append-only semantics cannot be unified into a single `save` method without losing domain meaning.

**Rules:** E6 (operations named in domain terms), E3 (preserve VO semantics).

### DD5: Strategies use MemoryService, not raw repositories

**Decision:** `StickyFactsStrategy` and `SummarizeOnThresholdStrategy` switch from direct `FactRepository`/`SummaryRepository` access to `MemoryService.provider(type)`.

**Why:** Memory is now a first-class domain concept. Strategies are consumers of memory, not owners of repositories. This also means adding new memory types doesn't require changing strategy constructor signatures.

**Rules:** V5 (domain services operate on domain concepts), E1 (BC boundary — strategies in Context Management BC access Memory BC through port).

### DD6: Implicit strategy-to-memory-type binding (no explicit config)

**Decision:** No `enabledMemoryTypes` on `AgentSession`. Strategies decide which memory types they use.

**Why:** Adding memory type config to AgentSession leaks Memory BC concepts into Conversation Context BC (E4 violation). The strategy IS the source of truth. User-level memory would exist outside sessions entirely, breaking the model.

**Rules:** E4 (aggregate boundary), V2 (keep AgentSession small), E1 (BC separation).

### DD7: Single memory.db

**Decision:** Consolidate `facts.db` and `summaries.db` into `memory.db` with two tables.

**Why:** One BC = one persistence store. Simplifies connection management, enables atomic `clearScope()` in one transaction, and adding new memory types means adding a table (not a database).

**Rules:** E1 (BC boundary), pragmatic simplicity.

### DD8: CleanupHandler uses MemoryService.clearScope()

**Decision:** `SessionDeletedCleanupHandler` moves to `memory-service` module. Calls `memoryService.clearScope(scope)` instead of individual repository deletes.

**Why:** Handler knows WHEN to clean (event reaction). MemoryService knows WHAT to clean (all providers). Adding new memory types doesn't require changing the handler.

**Rules:** V5 (separation of orchestration and domain logic), V4 (eventual consistency via domain events).

## Domain Model

### New Types (core/memory/)

```kotlin
/**
 * Scope of agent memory — defines storage boundary.
 * Value Object (E3): immutable, equality by attributes.
 */
sealed interface MemoryScope {
    /** Memory bound to a specific session. */
    data class Session(val sessionId: AgentSessionId) : MemoryScope
    // Future: User(userId), Branch(branchId)
}

/**
 * Memory type — type-safe key for provider registry lookup.
 * Sealed interface with phantom type parameter P carrying
 * the concrete provider type for compile-time safety.
 */
sealed interface MemoryType<P : MemoryProvider<*>> {
    data object Facts : MemoryType<FactMemoryProvider>
    data object Summaries : MemoryType<SummaryMemoryProvider>
}

/**
 * Provider for a specific memory type.
 * Domain Service interface (E6): stateless, domain-named operations.
 * Base interface covers read and cleanup. Write operations
 * are on specific sub-interfaces (different semantics per type).
 */
interface MemoryProvider<T> {
    suspend fun get(scope: MemoryScope): T
    suspend fun clear(scope: MemoryScope)
}

/**
 * Fact memory provider — replace-all write semantics.
 * Invariant: replace() deletes all existing facts for the scope
 * and writes the new list atomically.
 */
interface FactMemoryProvider : MemoryProvider<List<Fact>> {
    suspend fun replace(scope: MemoryScope, facts: List<Fact>)
}

/**
 * Summary memory provider — append-only write semantics.
 * Invariant: append() adds a new summary without touching existing ones.
 * delete() removes a specific summary.
 */
interface SummaryMemoryProvider : MemoryProvider<List<Summary>> {
    suspend fun append(scope: MemoryScope, summary: Summary)
    suspend fun delete(scope: MemoryScope, summary: Summary)
}

/**
 * Port for accessing agent memory (E6: Domain Service).
 * Registry-based facade: type-safe provider lookup by MemoryType,
 * scope-wide lifecycle management via clearScope.
 * Defined in core, implemented in domain/memory-service.
 */
interface MemoryService {
    fun <P : MemoryProvider<*>> provider(type: MemoryType<P>): P
    suspend fun clearScope(scope: MemoryScope)
}

/**
 * Immutable snapshot of all memory types in a scope.
 * Value Object (E3): equality by attributes.
 */
data class MemorySnapshot(
    val facts: List<Fact>,
    val summaries: List<Summary>,
)
```

### Relocated Types (no changes to models)

The following move from their current packages to `core/memory/model/`:
- `Fact`, `FactCategory`, `FactKey`, `FactValue` (from `core/fact/`)
- `Summary`, `SummaryContent` (from `core/summary/`)

The following stay in `core/session/` (shared VOs):
- `TurnIndex` — belongs to Conversation Context BC, referenced by Summary
- `CreatedAt` — shared utility VO

The following move to `core/memory/repository/`:
- `FactRepository` (from `core/fact/`)
- `SummaryRepository` (from `core/summary/`)

These become internal contracts for provider implementations, not public ports.

### Use Cases

```kotlin
/**
 * Get all agent memory for a session.
 * Application Use Case: orchestration, no business logic.
 */
interface GetMemoryUseCase {
    suspend fun execute(sessionId: AgentSessionId): MemorySnapshot
}

/**
 * Replace all facts for a session (replace-all semantics).
 * Application Use Case: delegates to FactMemoryProvider.replace().
 */
interface UpdateFactsUseCase {
    suspend fun execute(sessionId: AgentSessionId, facts: List<Fact>): Either<DomainError, Unit>
}

/**
 * Append a summary to a session (append-only semantics).
 * Application Use Case: delegates to SummaryMemoryProvider.append().
 */
interface AddSummaryUseCase {
    suspend fun execute(summary: Summary): Either<DomainError, Unit>
}

/**
 * Delete a specific summary from a session.
 * Application Use Case: delegates to SummaryMemoryProvider.delete().
 */
interface DeleteSummaryUseCase {
    suspend fun execute(summary: Summary): Either<DomainError, Unit>
}
```

## Module Structure

### Layer 0 — core

```
core/src/main/kotlin/com/ai/challenge/core/
├── memory/
│   ├── MemoryScope.kt
│   ├── MemoryType.kt
│   ├── MemoryProvider.kt          (base + FactMemoryProvider + SummaryMemoryProvider)
│   ├── MemoryService.kt
│   ├── MemorySnapshot.kt
│   ├── model/
│   │   ├── Fact.kt                (relocated from core/fact/)
│   │   ├── FactCategory.kt        (relocated)
│   │   ├── FactKey.kt             (relocated)
│   │   ├── FactValue.kt           (relocated)
│   │   ├── Summary.kt             (relocated from core/summary/)
│   │   └── SummaryContent.kt      (relocated)
│   ├── repository/
│   │   ├── FactRepository.kt      (relocated, internal contract)
│   │   └── SummaryRepository.kt   (relocated, internal contract)
│   └── usecase/
│       ├── GetMemoryUseCase.kt
│       ├── UpdateFactsUseCase.kt
│       ├── AddSummaryUseCase.kt
│       └── DeleteSummaryUseCase.kt
├── session/
│   ├── TurnIndex.kt               (stays here)
│   └── CreatedAt.kt               (stays here)
└── (other packages unchanged)
```

Old packages `core/fact/` and `core/summary/` are deleted.

### Layer 1 — data/memory-repository-exposed (new, replaces two modules)

```
modules/data/memory-repository-exposed/
└── src/main/kotlin/com/ai/challenge/memory/repository/
    ├── MemoryDatabase.kt           (single memory.db, creates both tables)
    ├── FactsTable.kt               (relocated from fact-repository-exposed)
    ├── SummariesTable.kt           (relocated from summary-repository-exposed)
    ├── ExposedFactRepository.kt    (relocated, updated imports)
    └── ExposedSummaryRepository.kt (relocated, updated imports)
```

Modules `fact-repository-exposed` and `summary-repository-exposed` are deleted.

### Layer 2 — domain/memory-service (new)

```
modules/domain/memory-service/
└── src/main/kotlin/com/ai/challenge/memory/service/
    ├── DefaultMemoryService.kt
    ├── provider/
    │   ├── DefaultFactMemoryProvider.kt
    │   └── DefaultSummaryMemoryProvider.kt
    ├── handler/
    │   └── SessionDeletedCleanupHandler.kt  (relocated from context-manager)
    └── usecase/
        ├── DefaultGetMemoryUseCase.kt
        ├── DefaultUpdateFactsUseCase.kt
        ├── DefaultAddSummaryUseCase.kt
        └── DefaultDeleteSummaryUseCase.kt
```

### Layer 2 — domain/context-manager (changes)

- `StickyFactsStrategy`: constructor `FactRepository` → `MemoryService`
- `SummarizeOnThresholdStrategy`: constructor `SummaryRepository` → `MemoryService`
- `SessionDeletedCleanupHandler`: removed (moved to memory-service)
- `FactExtractor`, `LlmFactExtractor`, `ContextCompressor`, `LlmContextCompressor`: unchanged
- Other strategies: unchanged

### Layer 3 — presentation/compose-ui (additions)

```
modules/presentation/compose-ui/src/main/kotlin/.../
└── debug/
    └── memory/
        ├── MemoryDebugComponent.kt
        ├── MemoryDebugStore.kt
        └── MemoryDebugScreen.kt
```

### Layer 3 — presentation/app (DI changes)

- Register `MemoryService`, `FactMemoryProvider`, `SummaryMemoryProvider`
- Register `MemoryDatabase` (single connection)
- Register memory use cases
- Remove old fact/summary repository DI bindings
- Update context-manager strategy DI to inject `MemoryService`

## Dependency Graph

```
presentation/app ──→ compose-ui ──→ core (use cases, MemorySnapshot)
       │
       ├──→ domain/memory-service ──→ core (MemoryService, providers)
       │         │
       │         └──→ data/memory-repository-exposed ──→ core (repositories)
       │
       └──→ domain/context-manager ──→ core (MemoryService port)
```

No circular dependencies. All dependencies top-to-bottom.

## Debug Panel (Presentation)

### MVIKotlin Store

```kotlin
sealed interface MemoryDebugIntent {
    data class LoadMemory(val sessionId: AgentSessionId) : MemoryDebugIntent
    data class ReplaceFacts(val facts: List<Fact>) : MemoryDebugIntent
    data class AddFact(val fact: Fact) : MemoryDebugIntent
    data class RemoveFact(val fact: Fact) : MemoryDebugIntent
    data class AddSummary(val summary: Summary) : MemoryDebugIntent
    data class DeleteSummary(val summary: Summary) : MemoryDebugIntent
}

data class MemoryDebugState(
    val facts: List<Fact>,
    val summaries: List<Summary>,
    val isLoading: Boolean,
    val error: String?,
)
```

### Decompose Component

```kotlin
interface MemoryDebugComponent {
    val state: StateFlow<MemoryDebugState>
    fun onIntent(intent: MemoryDebugIntent)
}
```

### UI Layout

- Side panel or tab alongside the chat
- Auto-loads memory for current session, reloads on session switch
- **Facts section:** Table with columns category | key | value. Inline editing. Add/remove rows.
- **Summaries section:** Card list showing content, turn range (from..to), timestamp. Add/remove cards.

## Data Migration

On first launch with new version:
1. Check if `facts.db` and `summaries.db` exist
2. Create `memory.db` with both tables
3. Copy data from old databases to new tables
4. Delete old databases (or rename as backup)

Migration logic lives in `MemoryDatabase.kt`.

## DDD Audit

| Rule | Verdict | Details |
|------|---------|---------|
| E1 — Bounded Context | OK | Memory BC with own language (scope, provider, memory type), clear boundaries |
| E2 — Entity | N/A | No new entities introduced |
| E3 — Value Object | OK | MemoryScope, MemoryType, MemorySnapshot — immutable. Fact, Summary unchanged |
| E4 — Aggregate | OK | No new aggregate. Memory = VOs with independent lifecycle, correlation via sessionId |
| E5 — Repository | OK | VO repositories with independent lifecycle in separate BC (documented exception) |
| E6 — Domain Service | OK | MemoryService, MemoryProvider — stateless, domain-named |
| E7 — Factory | N/A | Trivial construction |
| V1 — True Invariants | OK | No new invariants requiring aggregate |
| V2 — Small Aggregates | OK | No aggregate. Providers small and focused |
| V3 — Reference by ID | OK | AgentSessionId as correlation only. TurnIndex shared VO |
| V4 — Eventual Consistency | OK | SessionDeleted event → clearScope |
| V5 — App vs Domain | OK | Use cases = orchestration. MemoryService = domain. Handler = orchestration |
| V6 — Domain Events | OK | Uses existing SessionDeleted. No new events |
| V7 — Persist Whole Aggregate | N/A | No aggregate |

## Risks

1. **Unchecked cast in DefaultMemoryService** — Single cast in registry (heterogeneous map). Type safety guaranteed by sealed `MemoryType<P>`. Minimal risk.
2. **Data migration** — One-time migration from two DBs to one. Needs first-launch detection and error handling.
3. **Breaking imports** — Relocating Fact/Summary to new packages breaks imports across all modules. Mechanical work, no logic changes.
4. **Context-manager simplification** — Strategies lose direct repository access. Requires careful DI rewiring and test updates.

## Extensibility Examples

**Adding a new memory type (e.g., Embeddings):**
1. Add `data object Embeddings : MemoryType<EmbeddingMemoryProvider>` to sealed interface
2. Create `EmbeddingMemoryProvider` extending `MemoryProvider<List<Embedding>>`
3. Create `Embedding` VO in `core/memory/model/`
4. Create `DefaultEmbeddingMemoryProvider` in `memory-service`
5. Add table in `memory-repository-exposed`
6. Register in DI — `MemoryService` and `clearScope()` work automatically

**Adding a new scope (e.g., UserScope):**
1. Add `data class User(val userId: UserId) : MemoryScope` to sealed interface
2. Each provider's `get(scope)` / `clear(scope)` handles the new scope variant via `when`
3. Repositories add queries filtered by userId
