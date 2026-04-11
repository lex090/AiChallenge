# Sticky Facts Context Manager — Design Spec

## Overview

New `ContextManager` strategy: **Sticky Facts (Key-Value Memory)**. After each user message, an LLM extracts structured facts (goal, constraints, preferences, decisions, agreements) from the conversation. The LLM request includes: a system message with current facts + the last N turns + the new user message.

## Requirements

- Facts are extracted via LLM after every user message
- Facts are categorized: Goal, Constraint, Preference, Decision, Agreement
- Facts are persisted per session in a dedicated repository
- Each extraction returns the full updated fact set (add/update/remove)
- Context sent to LLM: system message with facts + last 5 turns + new message
- Integrates into existing `DefaultContextManager` as a new branch

## Domain Models (core module)

### ContextManagementType

Add new variant to existing sealed interface:

```kotlin
sealed interface ContextManagementType {
    data object None : ContextManagementType
    data object SummarizeOnThreshold : ContextManagementType
    data object StickyFacts : ContextManagementType
}
```

### FactCategory

```kotlin
enum class FactCategory {
    Goal,
    Constraint,
    Preference,
    Decision,
    Agreement,
}
```

New file: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactCategory.kt`

### FactId

```kotlin
@JvmInline
value class FactId(val value: String) {
    companion object {
        fun generate(): FactId = FactId(value = Uuid.random().toString())
    }
}
```

New file: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactId.kt`

### Fact

```kotlin
data class Fact(
    val id: FactId,
    val category: FactCategory,
    val key: String,
    val value: String,
)
```

New file: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/Fact.kt`

### FactRepository

```kotlin
interface FactRepository {
    suspend fun save(sessionId: AgentSessionId, facts: List<Fact>)
    suspend fun getBySession(sessionId: AgentSessionId): List<Fact>
    suspend fun deleteBySession(sessionId: AgentSessionId)
}
```

New file: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactRepository.kt`

`save()` performs full replacement: deletes existing facts for session, inserts new ones.

## Fact Extraction (context-manager module)

### FactExtractor interface

```kotlin
interface FactExtractor {
    suspend fun extract(
        currentFacts: List<Fact>,
        newUserMessage: String,
        lastAssistantResponse: String?,
    ): List<Fact>
}
```

New file: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/FactExtractor.kt`

### LlmFactExtractor

```kotlin
class LlmFactExtractor(
    private val service: OpenRouterService,
    private val model: String,
) : FactExtractor
```

New file: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/LlmFactExtractor.kt`

**LLM prompt structure:**

- System message: instructs the LLM to extract/update facts from the conversation. Lists the five categories. Specifies JSON output format.
- If currentFacts is non-empty: a user message with current facts as JSON
- If lastAssistantResponse is non-null: an assistant message with the response
- User message with the new user message
- Final user message: "Extract and return the updated facts as a JSON array."

**Expected LLM JSON response:**

```json
[
  {"category": "Goal", "key": "main_goal", "value": "Build a Kotlin chat bot"},
  {"category": "Constraint", "key": "language", "value": "Kotlin only, no Java"}
]
```

Parsed into `List<Fact>` with generated `FactId` values.

**Error handling:** If the LLM returns invalid JSON, `extract()` returns `currentFacts` unchanged (graceful fallback — no facts are lost).

## DefaultContextManager Changes

### New dependencies

```kotlin
class DefaultContextManager(
    private val contextManagementRepository: ContextManagementTypeRepository,
    private val compressor: ContextCompressor,
    private val summaryRepository: SummaryRepository,
    private val turnRepository: TurnRepository,
    private val factExtractor: FactExtractor,
    private val factRepository: FactRepository,
) : ContextManager
```

### New routing branch

In `prepareContext()`, add routing for `ContextManagementType.StickyFacts` to call private method `stickyFacts()`.

### stickyFacts() algorithm

1. Load current facts: `factRepository.getBySession(sessionId)`
2. Load turn history: `turnRepository.getBySession(sessionId)`
3. Get last assistant response from the last Turn (if exists)
4. Extract updated facts: `factExtractor.extract(currentFacts, newMessage, lastAssistantResponse)`
5. Persist updated facts: `factRepository.save(sessionId, updatedFacts)`
6. Take last 5 turns from history (fixed N = 5)
7. Build `PreparedContext`:
   - System message with formatted facts (if non-empty)
   - Last N turns as User/Assistant messages
   - New user message

### Facts system message format

```
You have the following context about this conversation:

## Goals
- main_goal: Build a Kotlin chat bot

## Constraints
- language: Kotlin only, no Java

## Preferences
- style: Prefers functional style

## Decisions
- framework: Chose Ktor for HTTP

## Agreements
- deadline: MVP by Friday
```

Categories with no facts are omitted from the message.

### PreparedContext fields

- `compressed = true` (context is reduced to facts + sliding window)
- `originalTurnCount` = total turns in session
- `retainedTurnCount` = min(totalTurns, 5)
- `summaryCount = 0`

## Data Layer: fact-repository-exposed

New module: `modules/data/fact-repository-exposed/`

### FactTable

```kotlin
object FactTable : Table("facts") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val category = varchar("category", 50)
    val key = varchar("key", 255)
    val value = text("value")

    override val primaryKey = PrimaryKey(id)
}
```

### ExposedFactRepository

```kotlin
class ExposedFactRepository(
    private val database: Database,
) : FactRepository
```

- `save()`: delete all facts for sessionId, then batch insert new facts
- `getBySession()`: SELECT where sessionId, map rows to `Fact`
- `deleteBySession()`: DELETE where sessionId

### DatabaseFactory

Creates SQLite database at `~/.ai-challenge/facts.db`. Same pattern as other repository modules.

### Category mapping

`Goal` -> `"goal"`, `Constraint` -> `"constraint"`, `Preference` -> `"preference"`, `Decision` -> `"decision"`, `Agreement` -> `"agreement"`.

## DI (AppModule)

### ExposedContextManagementTypeRepository mapping

Add: `ContextManagementType.StickyFacts` <-> `"sticky_facts"`

### New bindings in AppModule

```kotlin
single<FactRepository> { ExposedFactRepository(createFactDatabase()) }
single<FactExtractor> { LlmFactExtractor(service = get(), model = "google/gemini-2.0-flash-001") }
```

### Updated DefaultContextManager binding

Pass `factExtractor = get()` and `factRepository = get()` to constructor.

## Testing

### DefaultContextManager tests (existing file, new cases)

- StickyFacts + empty history: extracts facts from first message, returns system message + new message
- StickyFacts + history < 5 turns: all turns in window + facts in system message
- StickyFacts + history > 5 turns: only last 5 turns + facts, older turns discarded
- StickyFacts + fact update: verifies factRepository.save() called with updated facts
- StickyFacts + no facts extracted: no facts system message, only turns + new message

### LlmFactExtractor tests (new file)

- Correct prompt structure: validates request sent to LLM
- JSON parsing: valid JSON -> List<Fact>
- Empty response: empty JSON array -> empty list
- Invalid JSON: error handling

### ExposedFactRepository tests (new file)

- save + getBySession: round-trip
- save overwrites: second save replaces all facts
- deleteBySession: removes facts
- getBySession for empty session: returns empty list

### Fake implementations for testing

- `FakeFactExtractor`: returns preconfigured List<Fact>
- `FakeFactRepository`: in-memory Map<AgentSessionId, List<Fact>>
