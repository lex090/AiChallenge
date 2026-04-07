# Remove ContextStrategy and CompressionDecision

## Problem

After introducing `ContextManagementType`, the `ContextStrategy` / `CompressionDecision` / `CompressionContext` abstractions became redundant. `ContextManagementType` already describes what behavior to use; the strategy layer duplicates the same `when` dispatch without adding value.

## Solution

Inline the decision logic directly into `DefaultContextManager.prepareContext()` via `when(type)`.

## What Gets Deleted

### Core module files
- `ContextStrategy.kt` — `ContextStrategy` interface + `CompressionDecision` sealed interface
- `CompressionContext.kt` — `CompressionContext` data class

### Context-manager module files
- `ContextStrategyFactory.kt` — factory + `NoneContextStrategy`
- `SummarizeOnThresholdStrategy.kt` — threshold strategy implementation

### Test files
- `ContextStrategyFactoryTest.kt`
- `SummarizeOnThresholdStrategyTest.kt`

## What Changes

### `DefaultContextManager`
- Remove `strategyFactory` constructor parameter
- Inline decision logic into `prepareContext()`:
  - `ContextManagementType.None` → skip compression (existing `noCompression` / `reuseExistingSummary`)
  - `ContextManagementType.SummarizeOnThreshold` → evaluate threshold with hardcoded constants (`maxTurns=15`, `retainLast=5`, `compressionInterval=10`), compute `partitionPoint`, call `compress()`

### `AppModule`
- Remove `single { ContextStrategyFactory() }` binding
- Remove `strategyFactory` parameter from `DefaultContextManager` construction

### `DefaultContextManagerTest`
- Remove `ContextStrategyFactory()` from `createManager()`
- Existing tests already cover all scenarios (None, below threshold, at threshold, reuse summary, empty history)

## Design Decisions

- **Hardcoded constants** — `maxTurns`, `retainLast`, `compressionInterval` stay hardcoded inside `DefaultContextManager`. Extracting them is premature until there is a real need.
- **No intermediate abstractions** — with only two `ContextManagementType` variants, a `when` branch in the manager is sufficient. If types grow significantly, we can extract private methods later.
