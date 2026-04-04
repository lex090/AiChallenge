# CLAUDE.md

## Project Overview

AI Agent Chat — multi-module Kotlin/JVM desktop application. AI agent abstraction with pluggable UI (currently Compose Desktop).

## Architecture: Stratified Design

Each module = one architectural layer. Dependencies strictly top-to-bottom:

```
compose-ui  ->  ai-agent  ->  llm-service
week1  ->  llm-service
```

- **llm-service** — Data layer: OpenRouter HTTP client, request/response models, DSL
- **ai-agent** — Domain layer: Agent interface, error types (Arrow Either), implementations
- **compose-ui** — Presentation + UI: Decompose components, MVIKotlin stores, Compose screens, Koin DI
- **week1** — Demo tasks (standalone, not part of the main app)

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
OPENROUTER_API_KEY=<key> ./gradlew :compose-ui:run
```

## Build & Test

```bash
./gradlew build    # Full build
./gradlew test     # All tests
```
