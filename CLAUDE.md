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
- **Identity types** — AgentSessionId, BranchId, TurnId, ProjectId, UserId, UserNoteId
- **Shared VOs** — MessageContent, CreatedAt, UpdatedAt, ContextModeId (opaque strategy ID), SystemInstructions (project LLM instructions), TurnSnapshot (read-only Turn projection), PreparedContext, ContextMessage, MessageRole, LlmResponse, LlmUsage, ResponseFormat
- **Ports** — LlmPort, ContextManagerPort, TurnQueryPort (CM reads turns), ContextModeValidatorPort, SessionQueryPort (CM resolves userId from session), UserQueryPort (CM reads user preferences)
- **Events** — DomainEvent (TurnRecorded, SessionCreated, SessionDeleted, ProjectDeleted, ProjectInstructionsChanged, UserCreated, UserUpdated, UserDeleted), DomainEventPublisher, DomainEventHandler
- **Errors** — DomainError (sealed hierarchy with Arrow Either)

### Conversation BC — Domain (`modules/conversation/domain`)
Package: `com.ai.challenge.conversation`
- **Aggregate** — AgentSession (root, stores `contextModeId: ContextModeId`, `projectId: ProjectId?`, `userId: UserId?`), Branch, Turn, TurnSequence
- **Aggregate** — Project (root, stores `name: ProjectName`, `systemInstructions: SystemInstructions`)
- **Aggregate** — User (root, stores `name: UserName`, `preferences: UserPreferences`)
- **VOs** — SessionTitle, ProjectName, UserName, UserPreferences, UsageRecord, TokenCount, Cost
- **Services** — ChatService, SessionService, BranchService, UsageQueryService, ProjectService, UserService
- **Repositories** — AgentSessionRepository, ProjectRepository, UserRepository
- **Use Cases** — SendMessageUseCase, CreateSessionUseCase, DeleteSessionUseCase, ApplicationInitService, CreateProjectUseCase, UpdateProjectUseCase, DeleteProjectUseCase, ListProjectsUseCase, CreateUserUseCase, UpdateUserUseCase, DeleteUserUseCase, ListUsersUseCase
- **Implementations** — AiChatService, AiSessionService, AiBranchService, AiUsageQueryService, AiProjectService, AiUserService

### Conversation BC — Data (`modules/conversation/data`)
Package: `com.ai.challenge.conversation.data`
- ExposedAgentSessionRepository (Exposed + SQLite)
- ExposedProjectRepository (Exposed + SQLite)
- ExposedUserRepository (Exposed + SQLite)
- ExposedTurnQueryAdapter (implements TurnQueryPort, maps Turn → TurnSnapshot)
- SessionQueryAdapter (implements SessionQueryPort, resolves userId from session)
- UserQueryAdapter (implements UserQueryPort, reads user preferences)

### Context Management BC — Domain (`modules/context-management/domain`)
Package: `com.ai.challenge.contextmanagement`
- **Models** — Fact, FactCategory, FactKey, FactValue, Summary, SummaryContent, TurnIndex, ContextManagementType (with ContextModeId mapping), ContextStrategyConfig, ProjectInstructions, InstructionsContent, UserPreferencesMemory, UserNote, NoteTitle, NoteContent, UserFact
- **Repositories** — FactRepository, SummaryRepository, ProjectInstructionsRepository, UserPreferencesMemoryRepository, UserNoteRepository, UserFactRepository
- **Memory** — MemoryService, MemoryProvider, FactMemoryProvider, SummaryMemoryProvider, ProjectInstructionsMemoryProvider, UserPreferencesMemoryProvider, UserNoteMemoryProvider, UserFactMemoryProvider, MemoryType (Facts, Summaries, ProjectInstructions, UserPreferences, UserNotes, UserFacts), MemoryScope (Session, Project, User), MemorySnapshot
- **Strategies** — ContextStrategy, PassthroughStrategy, SlidingWindowStrategy, SummarizeOnThresholdStrategy, StickyFactsStrategy, BranchingContextManager, ContextPreparationAdapter (implements ContextManagerPort), ContextCompressorPort, FactExtractorPort, ContextModeValidatorAdapter, TurnSnapshotMapper
- **Use Cases** — GetMemoryUseCase, UpdateFactsUseCase, AddSummaryUseCase, DeleteSummaryUseCase
- **Implementations** — DefaultMemoryService, DefaultFactMemoryProvider, DefaultSummaryMemoryProvider, DefaultProjectInstructionsMemoryProvider, DefaultUserPreferencesMemoryProvider, DefaultUserNoteMemoryProvider, DefaultUserFactMemoryProvider, SessionDeletedCleanupHandler, ProjectInstructionsChangedHandler, ProjectDeletedCleanupHandler, UserUpdatedHandler, UserDeletedCleanupHandler, UserFactExtractionHandler

### Context Management BC — Data (`modules/context-management/data`)
Package: `com.ai.challenge.contextmanagement.data`
- ExposedFactRepository, ExposedSummaryRepository, ExposedProjectInstructionsRepository, ExposedUserPreferencesMemoryRepository, ExposedUserNoteRepository, ExposedUserFactRepository (Exposed + SQLite, memory.db)
- LlmContextCompressorAdapter, LlmFactExtractorAdapter (LLM adapters)

### Infrastructure (`modules/infrastructure/open-router-service`)
Package: `com.ai.challenge.infrastructure.llm`
- OpenRouterService, OpenRouterAdapter (LlmPort implementation)

### Presentation (`modules/presentation/*`)
- **compose-ui** — Decompose components, MVIKotlin stores, Compose screens. Pure UI, accesses data only through use case interfaces. Includes Memory debug panel, Project management (ProjectRail, ProjectSettingsPanel, ProjectListStore, ProjectSettingsStore), and User management (UserSettingsPanel, UserMemoryPanel, UserListStore, UserSettingsStore, UserMemoryStore, UserMemoryComponent).
- **app** — Application entry point, Koin DI configuration, InProcessDomainEventPublisher. Composition root that wires all layers together.

### Cross-Context Communication
- **Synchronous:** CM reads turns via `TurnQueryPort` → `ExposedTurnQueryAdapter` (returns `TurnSnapshot`, never `Turn`)
- **Asynchronous:** Conversation publishes `DomainEvent.SessionDeleted` → CM's `SessionDeletedCleanupHandler`; `DomainEvent.ProjectInstructionsChanged` → CM's `ProjectInstructionsChangedHandler` (upserts instructions in project-scoped memory); `DomainEvent.ProjectDeleted` → CM's `ProjectDeletedCleanupHandler` (cleans up project memory); `DomainEvent.UserUpdated` → CM's `UserUpdatedHandler` (syncs user preferences to CM memory); `DomainEvent.UserDeleted` → CM's `UserDeletedCleanupHandler` (cleans up user memory); `DomainEvent.TurnRecorded` → CM's `UserFactExtractionHandler` (extracts user facts via LLM)
- **Validation:** Application use cases validate `ContextModeId` via `ContextModeValidatorPort` → `ContextModeValidatorAdapter`

### Naming Convention: Port/Adapter
- All port interfaces suffixed with `Port` (LlmPort, TurnQueryPort, ContextManagerPort, ContextModeValidatorPort, ContextCompressorPort, FactExtractorPort, SessionQueryPort, UserQueryPort)
- All adapter implementations suffixed with `Adapter` (OpenRouterAdapter, ExposedTurnQueryAdapter, ContextPreparationAdapter, ContextModeValidatorAdapter, LlmContextCompressorAdapter, LlmFactExtractorAdapter, SessionQueryAdapter, UserQueryAdapter)

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

### Git: Never force-add ignored files

- **NEVER use `git add -f`** to add files that are in `.gitignore`. If `git add` skips a file, it is ignored intentionally.
- `docs/` is in `.gitignore` — do not track documentation files in git.
- When committing, use explicit file paths (`git add path/to/file.kt`), not `git add -A` or `git add .`.

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
