# CLAUDE.md

## Project Overview

AI Agent Chat — multi-module Kotlin/JVM desktop application. AI agent abstraction with pluggable UI (currently Compose Desktop).

## Architecture: Bounded Context Modules

Modules organized by DDD Bounded Context. Two BCs communicate through Shared Kernel (ports, events, IDs).

```
modules/
├── shared-kernel/                 ← Shared types between BCs
├── conversation/                  ← Conversation BC (Core Domain)
│   ├── domain/                    # Aggregate, services, use cases, impls
│   └── data/                      # ExposedAgentSessionRepository, ExposedTurnQueryAdapter
├── context-management/            ← Context Management BC (Supporting)
│   ├── domain/                    # Models, strategies, memory service, use cases
│   └── data/                      # Repositories, LLM adapters
├── infrastructure/
│   └── open-router-service/       ← LlmPort adapter (shared by both BCs)
├── presentation/
│   ├── compose-ui/                # Pure UI (components, stores, composables)
│   └── app/                       # Bootstrap, Koin DI, entry point
└── week1/                         ← Standalone
```

### Module Dependencies

```
shared-kernel              ← libs only (Arrow)
conversation/domain        ← shared-kernel
conversation/data          ← conversation/domain, shared-kernel, Exposed
context-management/domain  ← shared-kernel (NOT conversation!)
context-management/data    ← context-management/domain, shared-kernel, Exposed
infrastructure/open-router-service ← shared-kernel, Ktor
presentation/compose-ui    ← shared-kernel, conversation/domain, context-management/domain
presentation/app           ← all modules (composition root)
```

### Shared Kernel (`modules/shared-kernel`)
Package: `com.ai.challenge.sharedkernel`
- **Identity types** — AgentSessionId, BranchId, TurnId
- **Shared VOs** — MessageContent, CreatedAt, UpdatedAt, ContextModeId (opaque strategy ID), TurnSnapshot (read-only Turn projection), PreparedContext, ContextMessage, MessageRole, LlmResponse, LlmUsage, ResponseFormat
- **Ports** — LlmPort, ContextManagerPort, TurnQueryPort (CM reads turns), ContextModeValidatorPort
- **Events** — DomainEvent (TurnRecorded, SessionCreated, SessionDeleted), DomainEventPublisher, DomainEventHandler
- **Errors** — DomainError (sealed hierarchy with Arrow Either)

### Conversation BC — Domain (`modules/conversation/domain`)
Package: `com.ai.challenge.conversation`
- **Aggregate** — AgentSession (root, stores `contextModeId: ContextModeId`), Branch, Turn, TurnSequence
- **VOs** — SessionTitle, UsageRecord, TokenCount, Cost
- **Services** — ChatService, SessionService, BranchService, UsageQueryService
- **Repository** — AgentSessionRepository
- **Use Cases** — SendMessageUseCase, CreateSessionUseCase, DeleteSessionUseCase, ApplicationInitService
- **Implementations** — AiChatService, AiSessionService, AiBranchService, AiUsageQueryService

### Conversation BC — Data (`modules/conversation/data`)
Package: `com.ai.challenge.conversation.data`
- ExposedAgentSessionRepository (Exposed + SQLite)
- ExposedTurnQueryAdapter (implements TurnQueryPort, maps Turn → TurnSnapshot)

### Context Management BC — Domain (`modules/context-management/domain`)
Package: `com.ai.challenge.contextmanagement`
- **Models** — Fact, FactCategory, FactKey, FactValue, Summary, SummaryContent, TurnIndex, ContextManagementType (with ContextModeId mapping), ContextStrategyConfig
- **Repositories** — FactRepository, SummaryRepository
- **Memory** — MemoryService, MemoryProvider, FactMemoryProvider, SummaryMemoryProvider, MemoryType, MemoryScope, MemorySnapshot
- **Strategies** — ContextStrategy, PassthroughStrategy, SlidingWindowStrategy, SummarizeOnThresholdStrategy, StickyFactsStrategy, BranchingContextManager, ContextPreparationAdapter (implements ContextManagerPort), ContextCompressorPort, FactExtractorPort, ContextModeValidatorAdapter, TurnSnapshotMapper
- **Use Cases** — GetMemoryUseCase, UpdateFactsUseCase, AddSummaryUseCase, DeleteSummaryUseCase
- **Implementations** — DefaultMemoryService, DefaultFactMemoryProvider, DefaultSummaryMemoryProvider, SessionDeletedCleanupHandler

### Context Management BC — Data (`modules/context-management/data`)
Package: `com.ai.challenge.contextmanagement.data`
- ExposedFactRepository, ExposedSummaryRepository (Exposed + SQLite, memory.db)
- LlmContextCompressorAdapter, LlmFactExtractorAdapter (LLM adapters)

### Infrastructure (`modules/infrastructure/open-router-service`)
Package: `com.ai.challenge.infrastructure.llm`
- OpenRouterService, OpenRouterAdapter (LlmPort implementation)

### Presentation (`modules/presentation/*`)
- **compose-ui** — Decompose components, MVIKotlin stores, Compose screens. Pure UI, accesses data only through use case interfaces. Includes Memory debug panel.
- **app** — Application entry point, Koin DI configuration, InProcessDomainEventPublisher. Composition root that wires all layers together.

### Cross-Context Communication
- **Synchronous:** CM reads turns via `TurnQueryPort` → `ExposedTurnQueryAdapter` (returns `TurnSnapshot`, never `Turn`)
- **Asynchronous:** Conversation publishes `DomainEvent.SessionDeleted` → CM's `SessionDeletedCleanupHandler`
- **Validation:** Application use cases validate `ContextModeId` via `ContextModeValidatorPort` → `ContextModeValidatorAdapter`

### Naming Convention: Port/Adapter
- All port interfaces suffixed with `Port` (LlmPort, TurnQueryPort, ContextManagerPort, ContextModeValidatorPort, ContextCompressorPort, FactExtractorPort)
- All adapter implementations suffixed with `Adapter` (OpenRouterAdapter, ExposedTurnQueryAdapter, ContextPreparationAdapter, ContextModeValidatorAdapter, LlmContextCompressorAdapter, LlmFactExtractorAdapter)

### Standalone
- **week1** — Demo tasks (not part of the main app)

## Tech Stack

- Kotlin 2.3.20, JDK 21, Gradle 9.4.1
- Compose Multiplatform 1.10.3 (Desktop target)
- Ktor 3.4.2 (HTTP client)
- Decompose 3.5.0 (navigation, component lifecycle)
- MVIKotlin 4.3.0 (state management: Store, Intent, State)
- Koin 4.1.0 (DI)
- Arrow 2.1.2 (functional error handling with Either)
- Kotlinx Serialization 1.10.0
- Kotlinx Coroutines 1.10.2
- Exposed 0.61.0 (SQL framework, SQLite)
- SLF4J 2.0.17 (logging)
- Kotlinx Datetime 0.7.1

## Key Rules

### DDD Audit (mandatory)

- **When adding/modifying domain entities, aggregates, VOs, repositories, services, or events** — you MUST read `architecture/ddd-audit-checklist.md` and validate the change against formal DDD rules before implementation.
- Every design decision must cite a specific principle (Evans or Vernon), not subjective preference.

### DDD Documentation (mandatory)

- **Every new DDD building block** (Entity, Value Object, Aggregate, Domain Event, Repository, Domain Service, Port, Use Case) **MUST be documented** upon creation:
  1. **KDoc на классе/интерфейсе** — краткое описание: что это за элемент, какую доменную концепцию моделирует, к какому агрегату принадлежит (если применимо).
  2. **Инварианты** — задокументировать все бизнес-правила и инварианты, которые элемент защищает или гарантирует (в KDoc или отдельным комментарием перед валидацией).
  3. **Обновление CLAUDE.md** — добавить новый элемент в описание соответствующего Bounded Context в секции «Architecture: Bounded Context Modules», чтобы список моделей, сервисов и портов оставался актуальным.
- Без выполнения всех трёх пунктов добавление нового DDD-элемента считается незавершённым.

### Dependencies

- **All dependencies MUST go through Gradle Version Catalog** (`gradle/libs.versions.toml`). Never hardcode dependency coordinates directly in `build.gradle.kts` files.
- Add new library to `[libraries]` section with a `version.ref`, then reference via `libs.<name>` in build files.

### No Default Parameter Values

- **Do not use default parameter values** in class constructors, function declarations, or interface methods.
- All arguments must be passed explicitly at every call site.
- This applies to data classes, regular classes, functions, and interface methods.

### Named Arguments

- **All function and constructor calls MUST use named arguments** at every call site.
- This applies to all calls: constructors, regular functions, methods, factory functions.
- Example: `ContextMessage(role = MessageRole.User, content = text)` instead of `ContextMessage(MessageRole.User, text)`.

### Explicit Values at Call Sites

- **Never rely on default parameter values when calling external or standard library code.** Always pass all arguments explicitly.
- If a function has a default parameter, still provide the value explicitly at the call site.

### Repository Naming

- **Repository interfaces MUST be named `{DomainModel}Repository`** where `{DomainModel}` is the exact name of the domain model class they persist.
- Examples: `AgentSession` → `AgentSessionRepository`, `Fact` → `FactRepository`, `Summary` → `SummaryRepository`.

### Error Handling (Arrow Either)

- Use Arrow `Either<DomainError, T>` at domain boundaries (service interfaces).
- No `try/catch` in presentation layer.
- **Before writing or modifying Arrow code**, fetch the latest Arrow documentation via MCP context7 (`resolve-library-id` → `query-docs`) to verify current idiomatic patterns. Arrow API evolves between versions — do not rely on memorized patterns.
- **Never silently drop errors** — `is Either.Left -> {}` is forbidden. Every error must be surfaced (via `Msg.Error`, logging) or handled with an explicit fallback.
- Prefer Arrow DSL (`either { }`, `ensure`, `ensureNotNull`, `catch`, `bind`, `fold`, `getOrElse`) over manual `Either.Left/Right` construction and verbose `when` pattern matching.

### Presentation Layer (compose-ui)

- Decompose `ComponentContext` for lifecycle and navigation.
- MVIKotlin `Store` (Intent -> Executor -> Msg -> Reducer -> State) for state management.
- Compose `@Composable` functions only render state — no business logic.
- Presentation layer calls application use cases (SendMessageUseCase, CreateSessionUseCase, DeleteSessionUseCase), not domain services directly.
- `CoroutineExecutor` requires `kotlinx-coroutines-swing` on Desktop for `Dispatchers.Main`.

### Testing

- Unit tests with `kotlin-test` and `kotlinx-coroutines-test`.
- Fake HTTP responses via Ktor `MockEngine` (no mocking frameworks).
- Test MVIKotlin stores with `Dispatchers.setMain(StandardTestDispatcher())`.

## Running

```bash
OPENROUTER_API_KEY=<key> ./gradlew :modules:presentation:app:run
```

## Build & Test

```bash
./gradlew build    # Full build
./gradlew test     # All tests
```
