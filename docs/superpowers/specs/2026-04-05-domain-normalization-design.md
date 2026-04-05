# Domain Normalization & Repository Extraction

## Overview

Structural refactoring to normalize domain models, extract repositories, and centralize data access through the Agent facade.

## Goals

1. Normalize domain models — link by ID, not embed
2. Rename Manager → Repository, extract into separate modules
3. Remove InMemory implementations
4. Agent becomes the single data access point for UI
5. Remove `RequestMetrics` — use `TokenDetails` and `CostDetails` independently

## Module Structure (after refactoring)

```
core                        ← models, ID types, repository interfaces, Agent interface, AgentError
ai-agent                    ← OpenRouterAgent (Agent implementation)
session-repository-exposed  ← ExposedSessionRepository
turn-repository-exposed     ← ExposedTurnRepository
token-repository-exposed    ← ExposedTokenRepository
cost-repository-exposed     ← ExposedCostRepository
compose-ui                  ← UI layer (depends only on Agent)
llm-service                 ← OpenRouter HTTP client
week1                       ← demo
```

Module `session-storage` is deleted.

## Module Dependencies

```
compose-ui  →  ai-agent  →  core
                          →  llm-service

session-repository-exposed  →  core
turn-repository-exposed     →  core
token-repository-exposed    →  core
cost-repository-exposed     →  core

compose-ui  →  session-repository-exposed  (DI assembly only)
            →  turn-repository-exposed
            →  token-repository-exposed
            →  cost-repository-exposed

week1  →  llm-service
```

## Domain Models (in `core`)

### Value Classes (ID types)

- `SessionId` — UUID-wrapped, has `generate()`
- `TurnId` — UUID-wrapped, has `generate()`

### Data Classes

- `AgentSession` — `id: SessionId`, `title: String`, `createdAt: Instant`, `updatedAt: Instant` (no `history` field)
- `Turn` — `id: TurnId`, `userMessage: String`, `agentResponse: String`, `timestamp: Instant`
- `TokenDetails` — `promptTokens`, `completionTokens`, `cachedTokens`, `cacheWriteTokens`, `reasoningTokens` (all Int), computed `totalTokens`, operator `plus`
- `CostDetails` — `totalCost`, `upstreamCost`, `upstreamPromptCost`, `upstreamCompletionsCost` (all Double), operator `plus`
- `AgentError` — sealed interface: `NetworkError(message)`, `ApiError(message)`
- `AgentResponse` — `text: String`, `turnId: TurnId`, `tokenDetails: TokenDetails`, `costDetails: CostDetails`

### Removed

- `RequestMetrics` — replaced by direct use of `TokenDetails` and `CostDetails`

## Repository Interfaces (in `core`)

```kotlin
interface SessionRepository {
    suspend fun create(title: String): SessionId
    suspend fun get(id: SessionId): AgentSession?
    suspend fun delete(id: SessionId): Boolean
    suspend fun list(): List<AgentSession>
    suspend fun updateTitle(id: SessionId, title: String)
}

interface TurnRepository {
    suspend fun append(sessionId: SessionId, turn: Turn): TurnId
    suspend fun getBySession(sessionId: SessionId, limit: Int? = null): List<Turn>
    suspend fun get(turnId: TurnId): Turn?
}

interface TokenRepository {
    suspend fun record(sessionId: SessionId, turnId: TurnId, details: TokenDetails)
    suspend fun getByTurn(turnId: TurnId): TokenDetails?
    suspend fun getBySession(sessionId: SessionId): Map<TurnId, TokenDetails>
    suspend fun getSessionTotal(sessionId: SessionId): TokenDetails
}

interface CostRepository {
    suspend fun record(sessionId: SessionId, turnId: TurnId, details: CostDetails)
    suspend fun getByTurn(turnId: TurnId): CostDetails?
    suspend fun getBySession(sessionId: SessionId): Map<TurnId, CostDetails>
    suspend fun getSessionTotal(sessionId: SessionId): CostDetails
}
```

## Agent Interface (in `core`)

```kotlin
interface Agent {
    // messaging
    suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse>
    // sessions
    suspend fun createSession(title: String): SessionId
    suspend fun deleteSession(id: SessionId): Boolean
    suspend fun listSessions(): List<AgentSession>
    suspend fun getSession(id: SessionId): AgentSession?
    // turns
    suspend fun getTurns(sessionId: SessionId, limit: Int? = null): List<Turn>
    // tokens
    suspend fun getTokensByTurn(turnId: TurnId): TokenDetails?
    suspend fun getTokensBySession(sessionId: SessionId): Map<TurnId, TokenDetails>
    suspend fun getSessionTotalTokens(sessionId: SessionId): TokenDetails
    // cost
    suspend fun getCostByTurn(turnId: TurnId): CostDetails?
    suspend fun getCostBySession(sessionId: SessionId): Map<TurnId, CostDetails>
    suspend fun getSessionTotalCost(sessionId: SessionId): CostDetails
}
```

## OpenRouterAgent (in `ai-agent`)

### Constructor

```kotlin
class OpenRouterAgent(
    private val service: OpenRouterService,
    private val model: String,
    private val sessionRepository: SessionRepository,
    private val turnRepository: TurnRepository,
    private val tokenRepository: TokenRepository,
    private val costRepository: CostRepository,
) : Agent
```

### `send()` flow

1. Load history via `turnRepository.getBySession(sessionId)`
2. Build request with full chat history
3. Call `service.chat()`
4. Create `Turn`, save via `turnRepository.append(sessionId, turn)`
5. Save `TokenDetails` via `tokenRepository.record(turnId, ...)`
6. Save `CostDetails` via `costRepository.record(turnId, ...)`
7. Return `AgentResponse(text, turnId, tokenDetails, costDetails)`

### Delegation methods

All other Agent methods delegate to the corresponding repository.

## Repository Implementations

### Each module has its own `DatabaseFactory`

Each repository module independently creates/connects to `~/.ai-challenge/sessions.db`.

### `session-repository-exposed`

- `ExposedSessionRepository` implements `SessionRepository`
- Tables: `SessionsTable` (id PK, title, createdAt, updatedAt)

### `turn-repository-exposed`

- `ExposedTurnRepository` implements `TurnRepository`
- Tables: `TurnsTable` (id PK, sessionId, userMessage, agentResponse, timestamp)

### `token-repository-exposed`

- `ExposedTokenRepository` implements `TokenRepository`
- Tables: `TokenDetailsTable` (turnId PK, sessionId, promptTokens, completionTokens, cachedTokens, cacheWriteTokens, reasoningTokens)
- `sessionId` denormalized for independent `getBySession` queries

### `cost-repository-exposed`

- `ExposedCostRepository` implements `CostRepository`
- Tables: `CostDetailsTable` (turnId PK, sessionId, totalCost, upstreamCost, upstreamPromptCost, upstreamCompletionsCost)
- `sessionId` denormalized for independent `getBySession` queries

## UI Layer Changes (compose-ui)

### Principle

UI knows only about `Agent`. No direct repository dependencies in business logic.

### DI (Koin)

All repositories are assembled in `compose-ui` and injected into `OpenRouterAgent`. UI components receive only `Agent`.

### ChatStoreFactory

- Dependency: `Agent` only (removes `AgentSessionManager`, `UsageManager`)
- `LoadSession`: calls `agent.getTurns()`, `agent.getTokensBySession()`, `agent.getCostBySession()`
- `SendMessage`: calls `agent.send()` — returns `AgentResponse` with `tokenDetails` and `costDetails`

### SessionListStoreFactory

- Dependency: `Agent` only (removes `AgentSessionManager`)
- Uses `agent.listSessions()`, `agent.createSession()`, `agent.deleteSession()`

### RootComponent

- Receives `Agent`, no repository knowledge
- First session init via `agent.createSession()`

### UiMessage

- Stays in `compose-ui`, no changes

## Implementation Order

Each step produces a compilable state:

1. Create `core` module, move models and interfaces (rename Manager → Repository)
2. Create `session-repository-exposed`, move implementation
3. Create `turn-repository-exposed`, create implementation
4. Create `token-repository-exposed`, create implementation
5. Create `cost-repository-exposed`, create implementation
6. Extend Agent interface, update OpenRouterAgent
7. Update compose-ui — remove direct repository deps, use Agent only
8. Delete `session-storage` module
