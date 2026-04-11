# DDD Refactoring — Strict Domain-Driven Design (v2)

## Overview

Full DDD audit and refactoring of AI Agent Chat. Redefines bounded contexts, reclassifies aggregates/entities/value objects, introduces domain events with in-process dispatcher, application services layer, anti-corruption layer (LlmPort), strategy decomposition for context management, and comprehensive KDoc documentation of all DDD building blocks.

Supersedes the previous design (v1) which used pragmatic DDD. This version applies strict DDD per Evans and Vernon.

---

## 1. Bounded Contexts

Two bounded contexts identified based on Evans' criteria: own ubiquitous language, own model, clear integration points, autonomous evolution.

### 1.1. Conversation Context (Core Domain)

**Ubiquitous language:** Session, Branch, Turn, Message, "send message", "create branch", "delete session"

**Responsibility:** Core chat functionality — managing conversations with AI, session lifecycle, branching, usage tracking.

**Why a separate context:** This is the core domain. Everything revolves around conducting a conversation. Session, Branch, and Turn share a unified lifecycle: deleting a session cascades to all branches and turns. Creating a turn atomically updates branch's TurnSequence. Branch cannot exist without Session.

### 1.2. Context Management Context (Supporting Subdomain)

**Ubiquitous language:** Summary, Fact, PreparedContext, "compress context", "extract facts", "sliding window", "sticky facts" — terms absent from Conversation context.

**Responsibility:** Preparing and optimizing LLM context through various strategies (summarization, sliding window, fact extraction, branching passthrough).

**Why a separate context:** Facts and summaries are derived data generated from Turns but living by their own rules. Facts are fully recreated on each message (replace-all semantics). Summaries accumulate. Neither affects Conversation operations. Conversation context has zero knowledge of facts and summaries. Different lifecycle, different invariants, different persistence patterns.

### 1.3. Why Usage/Billing is NOT a separate context

`UsageRecord` is a value object embedded in Turn. `UsageQueryService` aggregates data from Turns. No own language (tokens and costs are part of Turn model), no own model, no separate lifecycle. This is a read-side concern within Conversation Context.

### 1.4. Why LLM Integration is NOT a separate context

OpenRouterService is an infrastructure adapter (Anti-Corruption Layer), not a bounded context. It translates `ChatRequest`/`ChatResponse` into domain models. ACL by Evans is a technical pattern at context boundary, not a separate context.

### 1.5. Why Fact is NOT part of AgentSession aggregate

**Rule 1: Aggregate = transactional consistency boundary.** When a user sends a message, Turn is created and appended to Branch.turnSequence in one transaction (must be atomic). Facts are recreated via a separate LLM call which can fail independently — this does NOT invalidate the Turn. Test: can all facts be deleted right now and AgentSession remains correct? Yes. Facts do not protect an aggregate invariant.

**Rule 2: Design small aggregates (Vernon).** Including Fact in AgentSession would make the aggregate grow with every message. At 100 turns, 50 facts, and 10 summaries — every transaction loads/validates the entire graph.

**Rule 3: Different lifecycle = different boundary.** Turn is immutable and created once. Fact is replace-all on every message. Turn always exists; Fact may not exist (when `ContextManagementType != StickyFacts`). Turn is used by Chat and UI; Fact is used only by ContextManager.

**Counterargument: "But Fact has sessionId!"** This is a correlation ID, not aggregate membership. Analogy: in e-commerce Order and Invoice both have customerId, but that doesn't make them part of Customer aggregate.

**Counterargument: "Deleting Session must delete Facts."** This is inter-context cleanup via Domain Events (`SessionDeleted`), not an aggregate invariant. Facts are already in a separate database (`facts.db` vs `sessions.db`), making atomic transactions physically impossible. The architecture already confirms these are different contexts.

### 1.6. Integration Map

```
Conversation Context                    Context Management Context
┌──────────────────────┐               ┌──────────────────────────┐
│                      │  publishes    │                          │
│  SessionService ─────┼──DomainEvent──▶  SessionDeletedCleanup  │
│  ChatService ────────┼──DomainEvent──▶  (optional incremental) │
│                      │               │                          │
│  ChatService ────────┼─ContextPrep───▶  ContextPreparationSvc  │
│                      │   Port        │                          │
│  AgentSessionRepo ───┼──TurnReader───▶  Strategies             │
│                      │               │                          │
│  LlmPort ◀───────────────────────────┼── LlmPort               │
│    │                 │               │    │                     │
└────┼─────────────────┘               └────┼─────────────────────┘
     │                                      │
     └──────────┬───────────────────────────┘
                ▼
     ┌─────────────────────┐
     │  OpenRouterAdapter   │  (Infrastructure — ACL)
     │  implements LlmPort  │
     └─────────────────────┘
```

**Dependency directions:**
- Conversation has zero dependency on Context Management
- Context Management depends on Conversation: reads Turns via `TurnReader` (read-only port)
- Both contexts use `LlmPort` (shared kernel — common LLM interface implemented in infrastructure)
- Context Management subscribes to Domain Events from Conversation via in-process event dispatcher

---

## 2. Conversation Context — Aggregate Model

### 2.1. Aggregate: AgentSession

```
AgentSession (Aggregate Root)
│
│  Identity: AgentSessionId (value class, UUID)
│  Invariants:
│  - Always has exactly one main Branch (sourceTurnId == null)
│  - Branch creation requires contextManagementType == Branching
│  - Main branch cannot be deleted
│  - Title generated from first message (if empty)
│
├── Branch (Entity)
│   │  Identity: BranchId (value class, UUID)
│   │  Invariants:
│   │  - isMain = (sourceTurnId == null)
│   │  - turnSequence is ordered and append-only
│   │  - sourceTurnId references an existing Turn
│   │
│   └── Turn (Entity, immutable)
│       Identity: TurnId (value class, UUID)
│       No own invariants — created once, never modified
│
Value Objects: SessionTitle, MessageContent, TurnSequence,
               UsageRecord, TokenCount, Cost, CreatedAt, UpdatedAt
```

### 2.2. Changes from Current Code

**1. Branch reclassified: Aggregate Root → Entity**

Current: Branch is marked as Aggregate Root with its own `BranchId`.

Why incorrect by DDD rules:
- Branch has `sessionId` — direct parent reference. An Aggregate Root does not reference another root if it is itself a root
- Branch has no independent lifecycle — deleting Session cascades all Branches
- `ensureDeletable()` checks "main cannot be deleted" — this is an AgentSession aggregate invariant
- Branch creation requires Session context (checking `contextManagementType`)
- All Branch operations go through `AgentSessionRepository` — one repository per aggregate

Change: Branch keeps `BranchId` (entities within an aggregate CAN have identity), but access only through AgentSession. Invariant check moves to root:

```kotlin
// AgentSession
fun ensureBranchDeletable(branch: Branch): Either<DomainError, Unit>
```

**2. Title generation moves from UI to domain**

Current: `ChatStoreFactory:164-166` checks empty title and sets `text.take(50)`.
Change: Move to `SendMessageUseCase` (application service).

**3. Application policy "always at least one session" → ApplicationInitService**

Current: `Main.kt:61-75` and `Main.kt:121-144`.
Change: Not a domain rule (domain doesn't care about 0 or 100 sessions). Move to `ApplicationInitService`.

**4. UsageRecord aggregation — through UsageQueryService, not in UI**

Current: `ChatStoreFactory:127` duplicates fold logic. `emptyUsageRecord()` duplicated in `ChatStoreFactory:262` and `AiUsageService:43`.
Change: Call `UsageQueryService.getSessionTotal(sessionId)`. Single `UsageRecord.ZERO` constant in domain.

**5. Domain Events published from domain services**

```
ChatService.send()      → publishes TurnRecorded(sessionId, turnId, branchId)
SessionService.create() → publishes SessionCreated(sessionId)
SessionService.delete() → publishes SessionDeleted(sessionId)
```

### 2.3. What Does NOT Change

- **AgentSessionRepository scope** — one repository per aggregate, correct
- **Turn stores sessionId** — deliberate compromise for query-performance in relational DB (documented)
- **Typed IDs** — `AgentSessionId`, `BranchId`, `TurnId` remain value classes
- **UsageRecord as embedded VO in Turn** — correct placement

---

## 3. Context Management Context — Detailed Model

### 3.1. Aggregates

**FactSheet (Aggregate Root)** — session-scoped collection of facts.

Identity: sessionId (AgentSessionId — correlation ID). Facts are always fully recreated as a single unit (`deleteAll` + `batchInsert`). FactSheet encapsulates this semantics — not just `List<Fact>`, but an object with `replaceAll(facts)` behavior.

**ConversationSummary (Aggregate Root)** — session-scoped collection of summaries.

Identity: sessionId (AgentSessionId — correlation ID). Different lifecycle from FactSheet: append-only vs replace-all. Different transactional boundary. Different invariants (summaries have order and non-overlapping ranges, facts do not).

```
FactSheet (Aggregate Root)
│  Identity: sessionId (correlation ID)
│  Invariants:
│  - Facts fully recreated on each message (replace-all)
│  - Each fact has category, key, value
│  - Empty FactSheet is valid
│
└── Fact (Value Object, no identity)
    Defined by: category + key + value

ConversationSummary (Aggregate Root)
│  Identity: sessionId (correlation ID)
│  Invariants:
│  - Summaries are append-only
│  - Ranges [fromTurnIndex..toTurnIndex] do not overlap
│  - Chronological order
│
└── Summary (Value Object, no identity)
    Defined by: content + fromTurnIndex + toTurnIndex + createdAt
```

### 3.2. Strategy Decomposition

Current problem: `DefaultContextManager` — 310 lines god-class with all 5 strategies inline, magic numbers, mixed persistence/logic. Violates SRP and Open-Closed Principle.

**Strategy interface:**

```kotlin
interface ContextStrategy {
    suspend fun prepare(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        config: ContextStrategyConfig,
    ): PreparedContext
}
```

**Orchestrator:**

```kotlin
class ContextPreparationService(
    strategies: Map<ContextManagementType, ContextStrategy>,
    sessionReader: SessionReader,
    configProvider: ContextStrategyConfigProvider,
) : ContextPreparationPort
```

**Concrete strategies (each a separate class):**
- `PassthroughStrategy` — returns full history without processing
- `SummarizeOnThresholdStrategy` — compresses when turns exceed threshold, saves Summary
- `SlidingWindowStrategy` — keeps last N turns
- `StickyFactsStrategy` — LLM-extracts facts, saves FactSheet, builds context with facts + recent turns
- `BranchingStrategy` — passthrough for current branch

### 3.3. Strategy Configuration — Sealed Hierarchy

Magic numbers replaced with typed configuration per strategy:

```kotlin
sealed interface ContextStrategyConfig {

    /** Passthrough — no processing. No parameters. */
    data object None : ContextStrategyConfig

    /**
     * Summarization on threshold.
     * @param maxTurnsBeforeCompression — turns count triggering compression
     * @param retainLastTurns — recent turns kept uncompressed
     * @param compressionInterval — minimum turns between compressions
     */
    data class SummarizeOnThreshold(
        val maxTurnsBeforeCompression: Int,
        val retainLastTurns: Int,
        val compressionInterval: Int,
    ) : ContextStrategyConfig

    /**
     * Sliding window — keeps only last N turns.
     * @param windowSize — number of turns in window
     */
    data class SlidingWindow(
        val windowSize: Int,
    ) : ContextStrategyConfig

    /**
     * Sticky facts — LLM-extracted facts between messages.
     * @param retainLastTurns — recent turns passed alongside facts
     */
    data class StickyFacts(
        val retainLastTurns: Int,
    ) : ContextStrategyConfig

    /** Branching — passthrough for current branch. No parameters. */
    data object Branching : ContextStrategyConfig
}
```

Benefits:
- **Exhaustive when** — compiler forces handling every type
- **Isolation** — SlidingWindow doesn't see `compressionInterval`, SummarizeOnThreshold doesn't see `windowSize`
- **Extensibility** — new strategy = new data class, zero impact on existing
- **Symmetry with ContextManagementType** — type and config go as a pair

### 3.4. Event Handlers

```kotlin
class SessionDeletedCleanupHandler(
    factSheetRepository: FactSheetRepository,
    conversationSummaryRepository: ConversationSummaryRepository,
) : DomainEventHandler<SessionDeleted>
```

Resolves orphaning problem: without this handler, facts and summaries persist in DB after session deletion.

---

## 4. Domain Events

### 4.1. Event Model

```kotlin
sealed interface DomainEvent {
    val sessionId: AgentSessionId
}

data class SessionCreated(override val sessionId: AgentSessionId) : DomainEvent
data class SessionDeleted(override val sessionId: AgentSessionId) : DomainEvent
data class TurnRecorded(
    override val sessionId: AgentSessionId,
    val turnId: TurnId,
    val branchId: BranchId,
) : DomainEvent
```

| Event | Publisher | When | Subscribers |
|-------|-----------|------|-------------|
| `SessionCreated` | SessionService.create() | After saving Session + main Branch | None currently (extensibility point) |
| `SessionDeleted` | SessionService.delete() | After deleting aggregate | SessionDeletedCleanupHandler: deletes FactSheet + Summaries |
| `TurnRecorded` | ChatService.send() | After saving Turn | Optional: incremental fact extraction |

### 4.2. Event Dispatch — In-Process, Synchronous

```kotlin
interface DomainEventPublisher {
    suspend fun publish(event: DomainEvent)
}

interface DomainEventHandler<T : DomainEvent> {
    suspend fun handle(event: T)
}
```

**Why synchronous, not async:**
1. **Scale** — desktop application, single user, no concurrent transactions
2. **Guarantees** — synchronous dispatch guarantees cleanup happens before `delete()` completes. Async could lose event on crash
3. **Simplicity** — no event store, retry, dead letter queue needed
4. **Migration** — `DomainEventPublisher` interface is abstract, can be replaced with async implementation without changing domain layer

---

## 5. Anti-Corruption Layer and Ports

### 5.1. LlmPort (in core module)

```kotlin
interface LlmPort {
    suspend fun complete(
        messages: List<ContextMessage>,
        responseFormat: ResponseFormat,
    ): Either<DomainError, LlmResponse>
}

data class LlmResponse(
    val content: MessageContent,
    val usage: UsageRecord,
)

sealed interface ResponseFormat {
    data object Text : ResponseFormat
    data object Json : ResponseFormat
}
```

Domain services work with domain types (`ContextMessage`, `MessageContent`), not with external API models (`ChatRequest`, `ChatResponse`). Allows replacing LLM provider without changing domain.

### 5.2. OpenRouterAdapter (in data/open-router-service)

```kotlin
class OpenRouterAdapter(
    openRouterService: OpenRouterService,
    model: String,
) : LlmPort
```

Responsibilities:
- Translates `ContextMessage` → `ChatRequest`
- Translates `ChatResponse` → `LlmResponse`
- Maps Double cost → BigDecimal `Cost`
- Maps Ktor exceptions → `DomainError.NetworkError` / `DomainError.ApiError`

Currently this mapping lives in `AiChatService:89-99` — it moves to the adapter.

### 5.3. Inter-Context Ports

```kotlin
/** Read-only access to Turns for Context Management Context. */
interface TurnReader {
    suspend fun getTurnsByBranch(branchId: BranchId): List<Turn>
    suspend fun getTurn(turnId: TurnId): Turn?
}

/** Context preparation for Conversation Context. */
interface ContextPreparationPort {
    suspend fun prepareContext(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
    ): PreparedContext
}
```

**Dependency directions:**
- `TurnReader`: defined in core, implemented in `domain/ai-agent` (delegates to `AgentSessionRepository`)
- `ContextPreparationPort`: defined in core, implemented in `domain/context-manager` (`ContextPreparationService`)

---

## 6. Application Services

### 6.1. Why Application Layer

Currently orchestration is spread across:
- `ChatStoreFactory` (presentation) calls `ChatService` + checks title + aggregates usage
- `Main.kt` (composition root) contains "always one session" policy
- `AiChatService` (domain) orchestrates context preparation + LLM + repository

By DDD: **Domain Service** contains business logic. **Application Service** orchestrates a use case, calling domain services in correct order and publishing events. Presentation calls ONLY Application Services.

```
Presentation (Store)
    │
    ▼
Application Service  ←  use case entry point
    │
    ├── Domain Service (business logic)
    ├── Repository (persistence)
    └── DomainEventPublisher (events)
```

### 6.2. Use Cases

**SendMessageUseCase**
- Orchestrates: gets PreparedContext from Context Management, delegates to ChatService for LLM call and Turn save, publishes TurnRecorded event, generates session title from first message (if empty)
- Absorbs: title generation from `ChatStoreFactory:164-166`, event publication

**CreateSessionUseCase**
- Orchestrates: creates AgentSession via SessionService, creates main Branch via BranchService, publishes SessionCreated event
- Absorbs: main branch creation from `AiSessionService:34-41`

**DeleteSessionUseCase**
- Orchestrates: deletes aggregate via SessionService, publishes SessionDeleted event (triggers cleanup in Context Management)
- Does NOT contain "always one session" policy

**ApplicationInitService**
- Contains application-level UX policies: "at least one session always exists", default session creation on first launch
- Absorbs: logic from `Main.kt:61-75` and `Main.kt:121-144`
- This is NOT a domain rule

**SwitchBranchUseCase**
- Orchestrates: loads turns for branch, loads usage for each turn, computes session total via UsageQueryService
- Absorbs: duplicated loading logic from `ChatStoreFactory:110-137` and `ChatStoreFactory:208-236`, usage aggregation from `ChatStoreFactory:127`

### 6.3. What Stays in Domain Services

| Domain Service | Responsibility |
|---|---|
| `ChatService` | LLM call via LlmPort + mapping response → Turn + saving Turn |
| `SessionService` | CRUD AgentSession, update title/contextManagementType |
| `BranchService` | Create/delete branches, validate invariants |
| `UsageQueryService` | Aggregate usage by turn/session (read-only, renamed from UsageService) |

### 6.4. What Stays in Presentation (Stores)

Stores become thin — only:
- Accept Intent from UI
- Call one Application Service
- Map result (Either) to State/Message for UI
- No business logic, no orchestration

---

## 7. Layer Dependency Map

```
core (Layer 0)
├── LlmPort                 ← defined here
├── ContextPreparationPort  ← defined here
├── TurnReader              ← defined here
├── DomainEventPublisher    ← defined here
├── DomainEvent hierarchy   ← defined here
└── all domain models, repositories interfaces, service interfaces

data/open-router-service (Layer 1)
└── OpenRouterAdapter       → implements LlmPort

data/session-repository-exposed (Layer 1)
└── ExposedAgentSessionRepository → implements AgentSessionRepository

data/fact-repository-exposed (Layer 1)
└── ExposedFactSheetRepository → implements FactSheetRepository

data/summary-repository-exposed (Layer 1)  [or cost-repository-exposed repurposed]
└── ExposedConversationSummaryRepository → implements ConversationSummaryRepository

domain/ai-agent (Layer 2)
├── ChatService impl        → uses LlmPort, ContextPreparationPort
├── SessionService impl     → uses AgentSessionRepository
├── BranchService impl      → uses AgentSessionRepository
├── UsageQueryService impl  → uses AgentSessionRepository
└── TurnReaderImpl          → implements TurnReader (delegates to AgentSessionRepository)

domain/context-manager (Layer 2)
├── ContextPreparationService → implements ContextPreparationPort
├── PassthroughStrategy       → uses TurnReader
├── SummarizeOnThresholdStrategy → uses TurnReader, LlmPort, ConversationSummaryRepository
├── SlidingWindowStrategy     → uses TurnReader
├── StickyFactsStrategy       → uses TurnReader, LlmPort, FactSheetRepository
├── BranchingStrategy         → uses TurnReader
└── SessionDeletedCleanupHandler → uses FactSheetRepository, ConversationSummaryRepository

presentation/app (Layer 3)
├── Application Services (SendMessageUseCase, CreateSessionUseCase, etc.)
├── InProcessDomainEventPublisher → implements DomainEventPublisher
├── DI wiring (Koin)
└── Event handler registration

presentation/compose-ui (Layer 3)
├── Stores → call ONLY Application Services
└── Composables → render state only, no business logic
```

---

## 8. KDoc Documentation Requirements

ALL DDD building blocks MUST have comprehensive KDoc documentation. This is a cross-cutting requirement applied to every class, interface, and file in domain layer.

### 8.1. Templates by DDD Type

**Aggregate Root:**
```kotlin
/**
 * Aggregate Root — [description of what this aggregate represents].
 *
 * Transactional boundary: [what changes atomically].
 *
 * Invariants:
 * - [invariant 1]
 * - [invariant 2]
 *
 * Child entities: [list with links]
 */
```

**Entity:**
```kotlin
/**
 * Entity — [description] within aggregate [AggregateName].
 *
 * Has stable identity [IdType], but is not an independent
 * Aggregate Root — access only through [RepositoryName].
 *
 * Lifecycle: [when created, when destroyed].
 *
 * Invariants:
 * - [invariant 1]
 */
```

**Value Object:**
```kotlin
/**
 * Value Object — [description].
 *
 * Has no identity — defined only by its attributes.
 * Immutable. [Additional behavior if any].
 *
 * [Where it is used / embedded].
 */
```

**Domain Service:**
```kotlin
/**
 * Domain Service — [what operation it performs].
 *
 * Orchestrates: [what components it coordinates].
 *
 * Contains no own state — all logic is stateless.
 */
```

**Repository:**
```kotlin
/**
 * Repository — sole access point to aggregate [AggregateName]
 * and its child entities [list].
 *
 * DDD rule: one repository per aggregate.
 * All operations with child entities go through this interface.
 */
```

**Domain Event:**
```kotlin
/**
 * Domain Event — the fact that [what happened].
 *
 * Subscribers:
 * - [Context]: [what it does in response]
 *
 * Published from [ServiceName] after [when].
 */
```

**Typed ID:**
```kotlin
/**
 * Typed identifier for [what it identifies].
 *
 * Value class over String (UUID). Ensures type safety —
 * impossible to accidentally pass [OtherId] instead of [ThisId].
 *
 * Generation: [generate] creates a new unique identifier.
 */
```

**Port:**
```kotlin
/**
 * Port — [what abstraction it provides].
 *
 * [ACL / inter-context description].
 * Implemented in [where].
 * Dependency direction: [who defines, who implements].
 */
```

**Application Service:**
```kotlin
/**
 * Application Service — [use case name].
 *
 * Orchestrates:
 * 1. [step 1]
 * 2. [step 2]
 * 3. [step 3]
 *
 * Presentation layer calls only this use case.
 */
```

**Event Handler:**
```kotlin
/**
 * Event Handler — [what it handles and why].
 *
 * Listens to: [EventType]
 * Actions: [what it does]
 *
 * Belongs to [Context Name] context.
 */
```

---

## 9. Complete DDD Classification

### 9.1. Conversation Context (Core Domain)

| Element | DDD Classification | Current Status | Change |
|---------|-------------------|----------------|--------|
| `AgentSession` | Aggregate Root | Correct | Add `ensureBranchDeletable()`, KDoc |
| `Branch` | Entity (within AgentSession) | **Incorrectly marked as Aggregate Root** | Reclassify, move invariant to root |
| `Turn` | Entity, immutable (within AgentSession) | Correct | KDoc |
| `AgentSessionId` | Typed ID (Value Object) | Correct | KDoc |
| `BranchId` | Typed ID (Value Object) | Correct | KDoc |
| `TurnId` | Typed ID (Value Object) | Correct | KDoc |
| `SessionTitle` | Value Object | Correct | KDoc |
| `MessageContent` | Value Object | Correct | KDoc |
| `TurnSequence` | Value Object | Correct | KDoc |
| `UsageRecord` | Value Object (embedded in Turn) | Correct | KDoc, add ZERO constant |
| `TokenCount` | Value Object | Correct | KDoc |
| `Cost` | Value Object | Correct | KDoc |
| `CreatedAt` | Value Object | Correct | KDoc |
| `UpdatedAt` | Value Object | Correct | KDoc |
| `ContextManagementType` | Value Object (sealed enum) | Correct | KDoc |
| `DomainError` | Value Object (sealed hierarchy) | Correct | KDoc |
| `DomainEvent` | Domain Event (sealed hierarchy) | Exists but unused | Wire through EventPublisher |
| `AgentSessionRepository` | Repository (one per aggregate) | Scope correct, impl overloaded | KDoc, internal decomposition |
| `ChatService` | Domain Service | Correct | Use LlmPort instead of OpenRouterService |
| `SessionService` | Domain Service | Correct | KDoc |
| `BranchService` | Domain Service | Correct | KDoc |
| `UsageQueryService` | Domain Service (read-only) | Currently named UsageService | Rename for clarity |
| `LlmPort` | Port (ACL) | **Does not exist** | Create in core |
| `LlmResponse` | Value Object | **Does not exist** | Create in core |
| `ResponseFormat` | Value Object | **Does not exist** | Create in core |
| `TurnReader` | Port (inter-context) | **Does not exist** | Create in core |
| `ContextPreparationPort` | Port | **Does not exist** | Create in core |
| `SendMessageUseCase` | Application Service | Logic spread across Store + Domain | Create |
| `CreateSessionUseCase` | Application Service | Logic in AiSessionService + Main.kt | Create |
| `DeleteSessionUseCase` | Application Service | Logic in Store + Main.kt | Create |
| `ApplicationInitService` | Application Service | Logic in Main.kt | Create |
| `SwitchBranchUseCase` | Application Service | Logic duplicated in Store | Create |

### 9.2. Context Management Context (Supporting Subdomain)

| Element | DDD Classification | Current Status | Change |
|---------|-------------------|----------------|--------|
| `FactSheet` | Aggregate Root | **Does not exist** | Create, wrap List\<Fact\> |
| `Fact` | Value Object (within FactSheet) | Correct in essence | Move under FactSheet |
| `FactCategory` | Value Object (enum) | Correct | KDoc |
| `FactKey` | Value Object | Correct | KDoc |
| `FactValue` | Value Object | Correct | KDoc |
| `ConversationSummary` | Aggregate Root | **Does not exist** | Create, wrap List\<Summary\> |
| `Summary` | Value Object (within ConversationSummary) | Correct in essence | Move under ConversationSummary |
| `SummaryContent` | Value Object | Correct | KDoc |
| `TurnIndex` | Value Object | Correct | KDoc |
| `ContextMessage` | Value Object | Correct | KDoc |
| `PreparedContext` | Value Object (output) | Correct | KDoc |
| `MessageRole` | Value Object (enum) | Correct | KDoc |
| `ContextStrategyConfig` | Value Object (sealed hierarchy) | **Does not exist** (magic numbers) | Create |
| `ContextPreparationService` | Domain Service (orchestrator) | DefaultContextManager (god class) | Split into orchestrator + strategies |
| `PassthroughStrategy` | Domain Service | Inline in DefaultContextManager | Extract to separate class |
| `SummarizeOnThresholdStrategy` | Domain Service | Inline in DefaultContextManager | Extract to separate class |
| `SlidingWindowStrategy` | Domain Service | Inline in DefaultContextManager | Extract to separate class |
| `StickyFactsStrategy` | Domain Service | Inline in DefaultContextManager | Extract to separate class |
| `BranchingStrategy` | Domain Service | Inline in DefaultContextManager | Extract to separate class |
| `LlmContextCompressor` | Infrastructure Service | Correct | Switch to LlmPort |
| `LlmFactExtractor` | Infrastructure Service | Correct | Switch to LlmPort |
| `FactSheetRepository` | Repository | Currently FactRepository | Rename, update contract |
| `ConversationSummaryRepository` | Repository | Currently SummaryRepository | Rename, update contract |
| `SessionDeletedCleanupHandler` | Event Handler | **Does not exist** (orphaning bug) | Create |

### 9.3. Shared Kernel (core module)

| Element | Purpose |
|---------|---------|
| `AgentSessionId`, `BranchId`, `TurnId` | Typed IDs — used by both contexts |
| `MessageContent`, `CreatedAt` | Value Objects — shared types |
| `DomainError` | Errors — unified sealed hierarchy |
| `DomainEvent` | Events — contract between contexts |
| `LlmPort`, `LlmResponse`, `ResponseFormat` | ACL port — both contexts call LLM |
| `TurnReader` | Inter-context port |
| `ContextPreparationPort` | Inter-context port |
| `DomainEventPublisher`, `DomainEventHandler` | Event infrastructure interfaces |

---

## 10. Issues Fixed by This Refactoring

1. **Branch misclassified as Aggregate Root** → Entity within AgentSession
2. **Business logic in presentation** (title generation, usage aggregation, "always one session" policy) → Application Services
3. **Orphaned Facts/Summaries on session deletion** → Domain Events + SessionDeletedCleanupHandler
4. **DefaultContextManager god-class (310 lines)** → Strategy Pattern with 5 dedicated classes + orchestrator
5. **Magic numbers in context management** → ContextStrategyConfig sealed hierarchy per strategy
6. **Direct dependency on OpenRouterService** → LlmPort ACL
7. **Duplicated history loading logic in Store** → SwitchBranchUseCase
8. **Duplicated emptyUsageRecord() constant** → Single UsageRecord.ZERO in domain
9. **Domain Events exist but unused** → Wired through DomainEventPublisher
10. **No application service layer** → Use cases extracted from presentation and domain
11. **No KDoc on DDD building blocks** → Comprehensive documentation on every class
