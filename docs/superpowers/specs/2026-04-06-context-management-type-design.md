# Context Management Type per Session

## Summary

Add a domain entity `ContextManagementType` (sealed interface) that defines how context is managed for a specific session. Store it in a dedicated repository. Provide UI for viewing and changing the type per session. Remove `CompressionStrategy` / `TurnCountStrategy` — replace with `ContextStrategy` / `SummarizeOnThresholdStrategy` created via factory from the domain type.

## Domain Model

### New entity: `ContextManagementType`

Location: `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementType.kt`

```kotlin
sealed interface ContextManagementType {
    data object None : ContextManagementType
    data object SummarizeOnThreshold : ContextManagementType
}
```

- `None` — full history sent as-is, no compression.
- `SummarizeOnThreshold` — compress via LLM summary when turn count exceeds threshold. Parameters (`maxTurns`, `retainLast`, `compressionInterval`) are internal defaults, not user-configurable.

### New repository: `ContextManagementRepository`

Location: `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementRepository.kt`

```kotlin
interface ContextManagementRepository {
    suspend fun save(sessionId: AgentSessionId, type: ContextManagementType)
    suspend fun getBySession(sessionId: AgentSessionId): ContextManagementType
    suspend fun delete(sessionId: AgentSessionId)
}
```

## Renames

| Before | After |
|--------|-------|
| `CompressionStrategy` (interface) | `ContextStrategy` |
| `TurnCountStrategy` | `SummarizeOnThresholdStrategy` |

`CompressionDecision`, `CompressionContext`, `ContextCompressor`, `CompressedContext` — remain unchanged (they describe the compression operation itself, not the management type).

## Strategy Factory

Location: `modules/domain/context-manager/`

```kotlin
class ContextStrategyFactory {
    fun create(type: ContextManagementType): ContextStrategy
}
```

- `None` → strategy that always returns `CompressionDecision.Skip`
- `SummarizeOnThreshold` → `SummarizeOnThresholdStrategy` with hardcoded default parameters

## ContextManager Changes

`DefaultContextManager` constructor changes:

- Remove: `strategy: CompressionStrategy`
- Add: `contextManagementRepository: ContextManagementRepository`, `strategyFactory: ContextStrategyFactory`

In `prepareContext`:
1. Load type from `contextManagementRepository.getBySession(sessionId)`
2. Create strategy via `strategyFactory.create(type)`
3. Continue with existing logic (evaluate → compress or skip)

## Data Layer: Repository Implementation

New module: `modules/data/context-management-repository-exposed/`

### Table: `context_management`

| Column | Type | Constraint |
|--------|------|------------|
| `session_id` | varchar(36) | PK |
| `type` | varchar | not null |

Type stored as string: `"none"`, `"summarize_on_threshold"`.

### `ExposedContextManagementRepository`

- `save` — upsert (insert or update) by `session_id`
- `getBySession` — select, map string to sealed type
- `delete` — delete by `session_id`

## Agent Interface Changes

New methods in `Agent`:

```kotlin
suspend fun getContextManagementType(sessionId: AgentSessionId): Either<AgentError, ContextManagementType>
suspend fun updateContextManagementType(sessionId: AgentSessionId, type: ContextManagementType): Either<AgentError, Unit>
```

### Session Lifecycle

- `createSession()` — after creating the session, automatically saves `ContextManagementType.None` via repository.
- `deleteSession()` — deletes context management record via `contextManagementRepository.delete(sessionId)`.

## UI: Session Settings Dialog

### SessionSettingsComponent (Decompose)

New component: `modules/presentation/compose-ui/.../settings/SessionSettingsComponent.kt`

- Receives `sessionId` and `Agent`
- Owns `SessionSettingsStore`

### SessionSettingsStore (MVIKotlin)

- **Intents:** `LoadSettings(sessionId)`, `ChangeContextManagementType(type)`
- **State:** `currentType: ContextManagementType`, `isLoading: Boolean`

### Dialog Content

- Title: "Session Settings"
- Radio buttons or dropdown for type selection: "No management" / "Summarize on threshold"
- Close button

### Entry Points

1. Gear icon button in chat toolbar
2. Gear icon or "..." button on session item in drawer

Both open the same `Dialog` composable over the current screen.
