# Memory Debug Panel Improvements Design

## Summary

Enhance the Memory Debug panel with context-aware display (show memory sections only for strategies that use them), CRUD operations for Facts (editable table with per-row Save/Delete and Add), category filtering for Facts, and read-only Summaries.

## Motivation

The current debug panel shows all memory types regardless of the active context management strategy. Facts are read-only with no way to edit, add, or remove them. No filtering by fact category. Summaries have a Delete button that shouldn't be there for a debug/observation tool.

## DDD Impact

None. All changes are in the presentation layer (compose-ui module). No new domain models, ports, use cases, or providers. Existing `SessionService`, `GetMemoryUseCase`, and `UpdateFactsUseCase` are sufficient.

## Design

### Context-Aware Display

The panel loads `contextManagementType` from the current session via `SessionService.get()`. Sections are shown/hidden based on strategy:

| contextManagementType | Facts | Summaries |
|---|---|---|
| StickyFacts | visible | hidden |
| SummarizeOnThreshold | hidden | visible |
| None, SlidingWindow, Branching | hidden | hidden |

When both sections are hidden, show message: "Current strategy does not use memory."

### Store Changes

#### State

```kotlin
data class State(
    val sessionId: AgentSessionId?,
    val contextManagementType: ContextManagementType?,
    val facts: List<Fact>,
    val summaries: List<Summary>,
    val selectedCategories: Set<FactCategory>,
    val isLoading: Boolean,
    val error: String?,
)
```

New fields:
- `contextManagementType` — determines which sections are visible
- `selectedCategories` — active category filters (all selected by default)

#### New Intents

```kotlin
// Category filtering
data class ToggleCategory(val category: FactCategory) : Intent
data object SelectAllCategories : Intent

// Facts CRUD
data class SaveFact(val index: Int, val fact: Fact) : Intent
data class DeleteFact(val index: Int) : Intent
data object AddFact : Intent
```

#### Removed Intents

- `ReplaceFacts` — replaced by per-row `SaveFact`
- `AddSummary` — summaries are read-only
- `DeleteSummary` — summaries are read-only

#### LoadMemory changes

`LoadMemory` additionally fetches the session via `SessionService.get()` to obtain `contextManagementType`. This is included in the `Msg.Loaded` message.

### DI Changes

`MemoryDebugStoreFactory` dependencies change:

**Added:** `SessionService` — to load `contextManagementType`

**Removed:** `AddSummaryUseCase`, `DeleteSummaryUseCase` — summaries are read-only

Updated constructor:
```kotlin
class MemoryDebugStoreFactory(
    private val storeFactory: StoreFactory,
    private val getMemoryUseCase: GetMemoryUseCase,
    private val updateFactsUseCase: UpdateFactsUseCase,
    private val sessionService: SessionService,
)
```

`Main.kt` updated accordingly.

### Facts UI — Editable Table

Layout per row:
```
[Category ▼] [key TextField] [value TextField] [💾 Save] [🗑 Delete]
```

Bottom of table:
```
[+ Add Fact]
```

**Category dropdown** — DropdownMenu with FactCategory enum values (Goal, Constraint, Preference, Decision, Agreement).

**Key/Value TextFields** — always editable. Local editing state managed via Compose `remember { mutableStateOf() }` per row. Not stored in Store State — avoids intent noise on every keystroke.

**Save button** — takes local editing state, creates a `Fact`, dispatches `SaveFact(index, fact)`. Executor builds full fact list with the updated row, calls `UpdateFactsUseCase` (replace-all semantics).

**Delete button** — dispatches `DeleteFact(index)`. Executor removes the fact at index, calls `UpdateFactsUseCase` with remaining list.

**Add Fact button** — dispatches `AddFact`. Executor creates a new Fact with category=Goal, key="", value="" using the current sessionId, adds to list, calls `UpdateFactsUseCase`.

### Category Filter Chips

Row of FilterChip components above the facts table:

```
[Goal ✓] [Constraint ✓] [Preference ✓] [Decision ✓] [Agreement ✓]  [All]
```

- Each chip is a toggle — `ToggleCategory` intent adds/removes from `selectedCategories`
- "All" chip — `SelectAllCategories` intent resets to all categories selected
- Table filters `facts` by `selectedCategories` in the composable (not in Store — filtering is pure UI concern)
- Filter state IS in Store (`selectedCategories`) so it persists across reloads

### Summaries UI — Read-Only

Cards showing turn range and content. No action buttons. Displayed only when `contextManagementType == SummarizeOnThreshold`.

```
┌──────────────────────────────┐
│ Turns 0..10                  │
│ Summary text content here... │
└──────────────────────────────┘
```

### File Changes

All in `modules/presentation/compose-ui/`:
- `debug/memory/MemoryDebugStore.kt` — updated State, new Intents
- `debug/memory/MemoryDebugStoreFactory.kt` — new Msg types, Executor logic for CRUD/filter, updated dependencies
- `debug/memory/MemoryDebugScreen.kt` — renamed to `MemoryDebugPanel` (already done), updated with editable table, filter chips, context-aware sections
- `debug/memory/MemoryDebugComponent.kt` — no changes needed

In `modules/presentation/app/`:
- `di/AppModule.kt` — update MemoryDebugStoreFactory DI (if registered there)
- `Main.kt` — update MemoryDebugStoreFactory constructor call
