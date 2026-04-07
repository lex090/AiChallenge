# CLAUDE.md

## Project Overview

AI Agent Chat — multi-module Kotlin/JVM desktop application. AI agent abstraction with pluggable UI (currently Compose Desktop).

## Architecture: Stratified Design

All modules live under `modules/`, organized by layer. Dependencies strictly top-to-bottom:

```
modules/
├── core/                          ← Layer 0: Foundation
├── data/                          ← Layer 1: Data
│   ├── open-router-service/
│   ├── session-repository-exposed/
│   ├── turn-repository-exposed/
│   ├── token-repository-exposed/
│   └── cost-repository-exposed/
├── domain/                        ← Layer 2: Domain
│   ├── ai-agent/
│   └── context-manager/
├── presentation/                  ← Layer 3: Presentation
│   ├── compose-ui/                # Pure UI (components, stores, composables)
│   └── app/                       # Bootstrap, DI, entry point
└── week1/                         ← Standalone
```

### Layer 0 — Foundation (`modules/core`)
- **core** — Domain models (AgentSession, Turn, TokenDetails, CostDetails), ID types, repository interfaces, Agent facade interface, AgentError (Arrow Either)

### Layer 1 — Data (`modules/data/*`)
- **open-router-service** — OpenRouter HTTP client, request/response models, DSL
- **session-repository-exposed** — SessionRepository implementation (Exposed + SQLite)
- **turn-repository-exposed** — TurnRepository implementation
- **token-repository-exposed** — TokenRepository implementation
- **cost-repository-exposed** — CostRepository implementation

### Layer 2 — Domain (`modules/domain/*`)
- **ai-agent** — OpenRouterAgent (Agent facade implementation), delegates to repositories
- **context-manager** — Context management: compression, summarization, LLM compressor

### Layer 3 — Presentation (`modules/presentation/*`)
- **compose-ui** — Decompose components, MVIKotlin stores, Compose screens. Pure UI, accesses data only through Agent interface.
- **app** — Application entry point, Koin DI configuration, bootstrap. Composition root that wires all layers together.

### Standalone
- **week1** — Demo tasks (not part of the main app)

No module may depend on a module above it.

## Tech Stack

- Kotlin 2.3.20, JDK 21, Gradle 9.4.1
- Compose Multiplatform 1.10.3 (Desktop target)
- Ktor 3.4.2 (HTTP client)
- Decompose 3.5.0 (navigation, component lifecycle)
- MVIKotlin 4.3.0 (state management: Store, Intent, State)
- Koin 4.1.0 (DI)
- Arrow 2.1.2 (functional error handling with Either)
- Kotlinx Serialization 1.10.0

## Key Rules

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
- Examples: `AgentSession` → `AgentSessionRepository`, `Turn` → `TurnRepository`, `ContextManagementType` → `ContextManagementTypeRepository`.

### Error Handling

- Use Arrow `Either<AgentError, T>` at domain boundaries (Agent interface).
- No try/catch in presentation layer — pattern-match on Either.

### Presentation Layer (compose-ui)

- Decompose `ComponentContext` for lifecycle and navigation.
- MVIKotlin `Store` (Intent -> Executor -> Msg -> Reducer -> State) for state management.
- Compose `@Composable` functions only render state — no business logic.
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
