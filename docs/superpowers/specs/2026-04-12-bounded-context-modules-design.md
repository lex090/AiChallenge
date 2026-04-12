# Bounded Context Module Extraction Design

## Problem

Current architecture has two conceptual Bounded Contexts (Conversation, Context Management) identified in the DDD refactoring spec, but they are not physically separated. All domain models and interfaces live in a single `modules/core`, and `modules/domain/*` are split by function, not by context boundary. This means:

- No compile-time enforcement of context boundaries
- Any module can use any core type, bypassing intended isolation
- Context Management concepts (Fact, Summary, strategies) are mixed with Conversation concepts in core

## Goal

Physically separate Bounded Contexts into independent module groups with explicit dependencies, enforced by Gradle module boundaries.

## Approach: Shared Kernel + Read Port

**Why this approach:**
- Context Management only reads Conversation data (never writes) — a Read Port accurately reflects this
- Single-process desktop app — Published Language and CQRS solve distributed problems that don't exist here
- Evolutionary path — Shared Kernel + Read Port can grow into Published Language later if needed

---

## Bounded Contexts

### Conversation Context (Core Domain)

**Ubiquitous Language:** session, branch, turn, "send message", "create session", "create branch"

**Aggregate:** `AgentSession` (root) -> `Branch` -> `Turn`

**Services:**
- `ChatService` — send message, get LLM response, create Turn
- `SessionService` — CRUD sessions
- `BranchService` — create/delete branches
- `UsageQueryService` — aggregate token/cost metrics

**Repository:** `AgentSessionRepository` (single, per aggregate)

**Application Use Cases:** `SendMessageUseCase`, `CreateSessionUseCase`, `DeleteSessionUseCase`, `ApplicationInitService`

**Domain Events (publishes):** `SessionCreated`, `SessionDeleted`, `TurnRecorded`

### Context Management Context (Supporting Subdomain)

**Ubiquitous Language:** fact, summary, context, strategy, "prepare context", "extract facts", "compress context"

**Domain Models:**
- `Fact` (VO) — extracted fact from conversation
- `Summary` (VO) — compressed summary of conversation segment

**Services:**
- `ContextPreparationAdapter` (implements `ContextManagerPort`) — strategy orchestrator
- 5 strategies: Passthrough, SlidingWindow, SummarizeOnThreshold, StickyFacts, Branching
- `MemoryService` — registry facade for memory providers
- `FactMemoryProvider`, `SummaryMemoryProvider`
- `LlmContextCompressorAdapter`, `LlmFactExtractorAdapter`

**Repositories:** `FactRepository`, `SummaryRepository`

**Application Use Cases:** `GetMemoryUseCase`, `UpdateFactsUseCase`, `AddSummaryUseCase`, `DeleteSummaryUseCase`

**Domain Events (subscribes):** `SessionDeleted` -> `SessionDeletedCleanupHandler`

---

## Shared Kernel

Minimal module with types shared by both BCs. Inclusion criteria: type is used by both BCs and does not belong to either.

### Contents

**Identity Types:**
- `AgentSessionId` — correlation ID between contexts
- `BranchId` — needed by CM to query turns by branch
- `TurnId` — references to specific turns

**Shared Value Objects:**
- `MessageContent` — message text (used by Conversation for Turn creation, CM for context preparation)
- `CreatedAt`, `UpdatedAt` — timestamps
- `ContextModeId` — opaque ID for context strategy selection (replaces `ContextManagementType` in shared kernel)
- `TurnSnapshot` — read-only VO returned from `TurnQueryPort` (contains `userMessage`, `assistantMessage`, `turnId`)
- `PreparedContext` — output of `ContextManagerPort` (list of messages + metadata)
- `ContextMessage` — message with role and content, used in prepared context
- `MessageRole` — enum: System, User, Assistant

**Cross-Context Ports:**
- `TurnQueryPort` — read-only interface for CM to read turns from Conversation
- `ContextModeValidatorPort` — validates `ContextModeId` values (implemented by CM)
- `ContextManagerPort` — context preparation interface (implemented by CM, consumed by Conversation)
- `LlmPort` — LLM abstraction (used by both contexts)

**Domain Event Infrastructure:**
- `DomainEvent` (sealed interface: `SessionCreated`, `SessionDeleted`, `TurnRecorded`)
- `DomainEventPublisher`, `DomainEventHandler`

**Error Handling:**
- `DomainError` — sealed error hierarchy

### What does NOT go into Shared Kernel

- `Turn`, `Branch`, `AgentSession` — Conversation aggregate internals
- `Fact`, `Summary` — belong to Context Management
- `ContextManagementType`, `ContextStrategyConfig` — CM internal types
- `MemoryService`, `MemoryType`, `MemoryScope` — CM internal abstractions

**Note on `PreparedContext`, `ContextMessage`, `MessageRole`:** These are the return/parameter types of `ContextManagerPort` (defined in SK). Since both Conversation (calls the port) and CM (implements the port) need these types, they belong in Shared Kernel alongside the port interface.

---

## ContextModeId Design (Opaque ID Pattern)

### Problem

`ContextManagementType` is semantically a CM concept (strategy selection), but stored in `AgentSession` (Conversation aggregate). Direct sharing creates coupling.

### Solution

**In Shared Kernel:**
```kotlin
@JvmInline
value class ContextModeId(val value: String)
```

**In Conversation:**
- `AgentSession` stores `contextModeId: ContextModeId`
- Conversation does not know which values are valid

**In Context Management:**
- CM defines `ContextManagementType` enum (None, SlidingWindow, StickyFacts, SummarizeOnThreshold, Branching)
- CM maps `ContextModeId` -> `ContextManagementType` internally
- Invalid `ContextModeId` -> `Either.Left(DomainError.UnknownContextMode)`

**Validation at Application Layer:**
- Application use cases call `ContextModeValidatorPort.isValid(contextModeId)` before saving to `AgentSession`
- `ContextModeValidatorAdapter` (in CM) checks against its enum

**UI Flow:**
- UI queries CM for available strategies
- User selects strategy -> UI passes `ContextModeId` to Conversation via use case
- Use case validates via `ContextModeValidatorPort` before saving

---

## Module Structure

```
modules/
├── shared-kernel/
│   └── src/main/kotlin/
│       ├── identity/        <- AgentSessionId, BranchId, TurnId
│       ├── model/           <- MessageContent, CreatedAt, UpdatedAt,
│       │                       ContextModeId, TurnSnapshot,
│       │                       PreparedContext, ContextMessage, MessageRole
│       ├── port/            <- LlmPort, TurnQueryPort, ContextModeValidatorPort,
│       │                       ContextManagerPort
│       ├── event/           <- DomainEvent, DomainEventPublisher, DomainEventHandler
│       └── error/           <- DomainError
│
├── conversation/
│   ├── domain/
│   │   └── src/             <- AgentSession, Branch, Turn, SessionTitle,
│   │                           TurnSequence, UsageRecord, TokenCount, Cost,
│   │                           ChatService, SessionService, BranchService,
│   │                           UsageQueryService, AgentSessionRepository,
│   │                           SendMessageUseCase, CreateSessionUseCase,
│   │                           DeleteSessionUseCase, ApplicationInitService
│   └── data/
│       └── src/             <- ExposedAgentSessionRepository,
│                               ExposedTurnQueryAdapter
│
├── context-management/
│   ├── domain/
│   │   └── src/             <- ContextManagementType, ContextStrategyConfig,
│   │                           PreparedContext, ContextMessage, MessageRole,
│   │                           Fact, FactCategory, FactKey, FactValue,
│   │                           Summary, SummaryContent, TurnIndex,
│   │                           MemoryService, MemoryType, MemoryScope,
│   │                           FactMemoryProvider, SummaryMemoryProvider,
│   │                           FactRepository, SummaryRepository,
│   │                           ContextCompressorPort, FactExtractorPort,
│   │                           ContextModeValidatorAdapter,
│   │                           strategies, ContextPreparationAdapter,
│   │                           DefaultMemoryService, providers, use case impls,
│   │                           SessionDeletedCleanupHandler
│   └── data/
│       └── src/             <- ExposedFactRepository, ExposedSummaryRepository,
│                               LlmContextCompressorAdapter, LlmFactExtractorAdapter
│
├── infrastructure/
│   └── open-router-service/ <- OpenRouterService, OpenRouterAdapter (LlmPort impl)
│
├── presentation/
│   ├── compose-ui/          <- UI components, stores
│   └── app/                 <- Koin DI, entry point
│
└── week1/                   <- standalone, no changes
```

## Module Dependencies

```
shared-kernel              <- libs only (Arrow, kotlinx-datetime)
conversation/domain        <- shared-kernel
conversation/data          <- conversation/domain, shared-kernel, Exposed
context-management/domain  <- shared-kernel (NOT conversation!)
context-management/data    <- context-management/domain, shared-kernel, Exposed, Ktor
infrastructure/open-router-service <- shared-kernel, Ktor
presentation/compose-ui    <- shared-kernel, conversation/domain, context-management/domain
presentation/app           <- all modules (composition root)
```

**Key invariant:** `context-management/domain` depends on `shared-kernel` but **never** on `conversation/domain`. CM reads turns through `TurnQueryPort` from shared-kernel, receiving `TurnSnapshot` — never sees `Turn`, `Branch`, `AgentSession`.

---

## Cross-Context Communication

### Synchronous: TurnQueryPort (CM reads from Conversation)

```kotlin
// shared-kernel
interface TurnQueryPort {
    suspend fun getTurnSnapshots(
        sessionId: AgentSessionId,
        branchId: BranchId
    ): List<TurnSnapshot>
}

data class TurnSnapshot(
    val turnId: TurnId,
    val userMessage: MessageContent,
    val assistantMessage: MessageContent
)
```

- `ExposedTurnQueryAdapter` in `conversation/data` — delegates to `AgentSessionRepository`
- CM strategies call `TurnQueryPort` instead of `AgentSessionRepository`

### Asynchronous: Domain Events (Conversation -> CM)

```kotlin
// shared-kernel
sealed interface DomainEvent {
    data class SessionDeleted(val sessionId: AgentSessionId) : DomainEvent
    data class SessionCreated(val sessionId: AgentSessionId) : DomainEvent
    data class TurnRecorded(
        val sessionId: AgentSessionId,
        val turnSnapshot: TurnSnapshot,
        val branchId: BranchId
    ) : DomainEvent
}
```

- `SessionDeletedCleanupHandler` in CM subscribes to `SessionDeleted`
- `TurnRecorded` passes `TurnSnapshot` (not `Turn`) — CM does not depend on Conversation models

### Validation: ContextModeValidatorPort

```kotlin
// shared-kernel
fun interface ContextModeValidatorPort {
    fun isValid(contextModeId: ContextModeId): Boolean
}
```

- `ContextModeValidatorAdapter` in `context-management/domain`
- Called from application use cases when updating session context mode

---

## Migration Map

### Current modules -> New modules

| Current Module | Destination |
|---------------|-------------|
| `modules/core` | Split into `shared-kernel` + `conversation/domain` + `context-management/domain` |
| `modules/data/session-repository-exposed` | -> `conversation/data` |
| `modules/data/memory-repository-exposed` | -> `context-management/data` |
| `modules/data/open-router-service` | -> `infrastructure/open-router-service` (stays separate) |
| `modules/domain/ai-agent` | -> `conversation/domain` (service implementations) |
| `modules/domain/context-manager` | -> `context-management/domain` (strategies, orchestrator) |
| `modules/domain/memory-service` | -> `context-management/domain` (memory service, providers, use cases) |

### Renames (Port/Adapter convention)

| Current Name | New Name | Location |
|-------------|----------|----------|
| `ContextManager` | `ContextManagerPort` | shared-kernel |
| `ContextCompressor` | `ContextCompressorPort` | context-management/domain |
| `FactExtractor` | `FactExtractorPort` | context-management/domain |
| `LlmContextCompressor` | `LlmContextCompressorAdapter` | context-management/data |
| `LlmFactExtractor` | `LlmFactExtractorAdapter` | context-management/data |
| `ContextPreparationService` | `ContextPreparationAdapter` | context-management/domain |
| — (new) | `ExposedTurnQueryAdapter` | conversation/data |
| — (new) | `ContextModeValidatorAdapter` | context-management/domain |

### New types

| Type | Location | Purpose |
|------|----------|---------|
| `ContextModeId` | shared-kernel | Opaque ID for context strategy, replaces `ContextManagementType` in SK |
| `TurnSnapshot` | shared-kernel | Read-only projection of Turn for cross-context use |
| `TurnQueryPort` | shared-kernel | Read port for CM to access turns |
| `ContextModeValidatorPort` | shared-kernel | Validation port for context mode IDs |
| `ExposedTurnQueryAdapter` | conversation/data | TurnQueryPort implementation |
| `ContextModeValidatorAdapter` | context-management/domain | ContextModeValidatorPort implementation |

---

## Presentation Layer Changes

### compose-ui

Dependencies after refactoring:
- `shared-kernel` — for `AgentSessionId`, `BranchId`, `ContextModeId`, `MessageContent`
- `conversation/domain` — for `SessionService`, `ChatService`, `BranchService`, `UsageQueryService`, use case interfaces, `AgentSession`, `Branch`, `Turn`, `UsageRecord`
- `context-management/domain` — for `GetMemoryUseCase`, `UpdateFactsUseCase`, `Fact`, `Summary`, `ContextManagementType` (for memory debug panel and strategy selection in UI)

### app (Composition Root)

Depends on all modules (unchanged role). Koin wiring updates:
- New bindings: `TurnQueryPort` -> `ExposedTurnQueryAdapter`, `ContextModeValidatorPort` -> `ContextModeValidatorAdapter`
- Remove direct `AgentSessionRepository` injection into CM strategies — replaced by `TurnQueryPort`
- Application use cases validate `contextModeId` via `ContextModeValidatorPort` before saving

---

## DDD Validation

| Principle | Status | Notes |
|-----------|--------|-------|
| Evans — Bounded Context | OK | Each BC has own ubiquitous language, models, repositories |
| Evans — Shared Kernel | OK | Minimal, ~12 types, genuinely shared |
| Vernon — Aggregate Design | OK | AgentSession intact in Conversation, Fact/Summary as VOs in CM |
| Vernon — Repository per Aggregate | OK | AgentSessionRepository in Conversation, Fact/SummaryRepository in CM |
| Dependency Direction | OK | CM -> SK <- Conversation, no circular deps |
| No Shared Kernel bloat | OK | ContextManagementType moved to CM, replaced by opaque ContextModeId |
| No leaking internals | OK | Turn never leaves Conversation, CM receives TurnSnapshot |
| Port/Adapter naming | OK | All ports suffixed Port, all adapters suffixed Adapter |

---

## Backend Perspective

This section analyzes how the architecture would change if this were a backend application instead of a desktop app.

### What stays the same

The Bounded Context boundaries, Shared Kernel contents, and module dependency graph would remain identical. DDD strategic design is transport-agnostic — the domain layer does not change based on whether the consumer is a Compose UI or REST API.

### What changes

**Presentation layer replacement:**
```
modules/presentation/     (current: desktop)     ->    modules/api/          (backend)
├── compose-ui/           (Compose screens)             ├── rest/            (REST controllers)
└── app/                  (Koin + Compose bootstrap)    └── app/             (Koin + server bootstrap)
```

**Service topology if deployed as microservices:**
```
┌─────────────────────────┐     ┌──────────────────────────────┐
│  Conversation Service   │     │  Context Management Service  │
│  ───────────────────    │     │  ──────────────────────────  │
│  shared-kernel          │     │  shared-kernel               │
│  conversation/domain    │     │  context-management/domain   │
│  conversation/data      │     │  context-management/data     │
│  infrastructure/llm     │     │  infrastructure/llm          │
│  api/rest (sessions,    │     │  api/rest (facts, summaries, │
│   branches, chat)       │     │   strategies, context)       │
│  Own PostgreSQL DB      │     │  Own PostgreSQL DB            │
└────────┬────────────────┘     └──────────────┬───────────────┘
         │                                     │
         │  TurnQueryPort -> HTTP/gRPC call     │
         │  DomainEvents -> Message broker      │
         └─────────────────────────────────────┘
```

**Key differences in backend deployment:**

1. **TurnQueryPort** — currently in-process method call. In microservices: HTTP/gRPC client adapter in CM service calling Conversation service API. The port interface stays the same, only the adapter changes.

2. **Domain Events** — currently `InProcessDomainEventPublisher` (synchronous, in-memory). In microservices: Kafka/RabbitMQ/NATS. `SessionDeleted` becomes an async message. Eventual consistency becomes real (not just theoretical).

3. **ContextModeValidatorPort** — currently in-process call. In microservices: either HTTP call to CM service, or CM publishes its list of valid modes as a shared config/schema.

4. **Database** — currently shared SQLite files. In microservices: each service owns its database (sessions.db stays with Conversation, memory.db stays with CM). This is already the case in current implementation — clean split.

5. **LlmPort** — each service would have its own LLM adapter instance. Infrastructure module gets duplicated per service (or becomes a shared library).

### Why the current design enables this

The opaque `ContextModeId` pattern, `TurnQueryPort` read interface, and domain event communication are exactly the patterns that make microservice extraction possible without domain model changes. The domain modules would be copied as-is into separate services — only infrastructure adapters change.
