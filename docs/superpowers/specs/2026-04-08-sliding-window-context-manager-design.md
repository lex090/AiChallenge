# Sliding Window Context Manager

## Overview

New `ContextManagementType.SlidingWindow` strategy that keeps only the last N turns, discarding older history without summarization.

## Motivation

Lightweight alternative to `SummarizeOnThreshold` — no LLM calls for compression, predictable token usage, simple behavior.

## Design

### Core: `ContextManagementType.SlidingWindow`

New `data object` in the existing sealed interface:

```kotlin
sealed interface ContextManagementType {
    data object None : ContextManagementType
    data object SummarizeOnThreshold : ContextManagementType
    data object SlidingWindow : ContextManagementType
}
```

No parameters — window size is a constant in `DefaultContextManager`.

### Domain: `DefaultContextManager`

New constant:

```kotlin
private const val WINDOW_SIZE = 10
```

New branch in `prepareContext` routing:

```kotlin
is ContextManagementType.SlidingWindow -> slidingWindow(sessionId, newMessage)
```

`slidingWindow()` method:
1. Fetch all turns via `TurnRepository.getBySession(sessionId)`
2. `turns.takeLast(WINDOW_SIZE)` — keep last 10
3. Convert via existing `turnsToMessages()`
4. Append new user message
5. Return `PreparedContext(messages, compressed = false, originalTurnCount = allTurns.size, retainedTurnCount = windowTurns.size, summaryCount = 0)`

No interaction with `SummaryRepository` or `ContextCompressor`.

### Data: `ExposedContextManagementTypeRepository`

Serialization mapping:

- `toStorageString()`: `is ContextManagementType.SlidingWindow -> "sliding_window"`
- `toContextManagementType()`: `"sliding_window" -> ContextManagementType.SlidingWindow`

No schema changes — `type` column is `VARCHAR(50)`.

### Presentation: `SessionSettingsContent`

New radio button option in the settings panel:

```kotlin
ContextManagementTypeOption(
    label = "Sliding window",
    description = "Keep last 10 turns, discard older",
    selected = state.currentType is ContextManagementType.SlidingWindow,
    onClick = { component.onChangeType(ContextManagementType.SlidingWindow) },
)
```

Store, Component, Agent — no changes needed. They already work with `ContextManagementType` generically.

### Testing

Tests in `DefaultContextManagerTest`:

1. **History smaller than window** — 5 turns, all returned (behaves like pass-through)
2. **History larger than window** — 15 turns, last 10 returned + new message
3. **Empty history** — 0 turns, only new user message

Assertions on `PreparedContext`: `compressed = false`, correct `originalTurnCount`, `retainedTurnCount`, `summaryCount = 0`.

## Files Changed

| File | Change |
|------|--------|
| `modules/core/.../ContextManagementType.kt` | Add `SlidingWindow` data object |
| `modules/domain/context-manager/.../DefaultContextManager.kt` | Add `WINDOW_SIZE` constant, `slidingWindow()` method, routing branch |
| `modules/data/context-management-repository-exposed/.../ExposedContextManagementTypeRepository.kt` | Add serialization branches |
| `modules/presentation/compose-ui/.../SessionSettingsContent.kt` | Add radio button option |
| `modules/domain/context-manager/.../DefaultContextManagerTest.kt` | Add 3 test cases |
