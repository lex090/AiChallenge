# Context Management Strategies Design

## Overview

Three context management strategies for the AI agent with a UI switcher in Compose. Each strategy controls how conversation history is prepared before sending to the LLM. An automated integration test scenario compares strategies on quality, stability, token usage, and user experience.

Three architectural approaches are developed in parallel (git worktrees) to find the best fit.

## Strategies

### Strategy 1: Sliding Window

Keep only the last N turns. Discard everything else without summarization.

- No LLM calls for compression
- Cheapest in tokens
- Loses all context beyond the window

### Strategy 2: Sticky Facts / Key-Value Memory

Maintain a `facts` block (key-value pairs) extracted from conversation. Send facts + last N turns to the LLM.

- After each user message, call LLM to extract/update facts (goal, constraints, preferences, decisions, agreements)
- Facts stored persistently per session
- Context = system message with facts + last N turns
- More expensive (extra LLM call per message) but retains important details

### Strategy 3: Branching

Save checkpoints in conversation. Create independent branches from any checkpoint. Continue dialogue in each branch independently. Switch between branches.

- Checkpoint = snapshot of conversation state at a specific turn
- Branch = independent continuation from a checkpoint
- Visual tree UI showing branches and checkpoints
- Each branch has its own turn history from the checkpoint onward

## UI Requirements

- Dropdown/selector in Compose UI to switch active strategy (Sliding Window / Facts / Branching)
- Strategy switch applies to the current session
- Branching strategy shows additional UI: checkpoint button, branch tree visualization, branch switcher
- Facts strategy shows current facts panel (read-only)

## Automated Comparison Test

Integration test that runs the same scenario (10-15 messages simulating requirements gathering) through each strategy:

- Uses MockEngine for deterministic LLM responses
- Measures: token count per strategy, number of LLM calls, retained context details
- Outputs comparison report: quality score, stability (fact retention), token usage, call count
- Runs as a standard `./gradlew test` task

---

## Approach 1: Extending Current Abstraction

Evolve existing `ContextManager` interface with new implementations.

### Architecture

```
ContextManager (interface, unchanged)
├── DefaultContextManager (existing, compression-based)
├── SlidingWindowContextManager (new, trim only)
├── StickyFactsContextManager (new, facts + trim)
└── BranchingContextManager (new, branch-aware)

DelegatingContextManager (new)
└── delegates to active ContextManager, switchable at runtime
```

### New Core Models

```kotlin
data class Fact(
    val key: String,
    val value: String,
    val updatedAt: Instant,
)

data class Branch(
    val id: BranchId,
    val sessionId: SessionId,
    val name: String,
    val checkpointTurnIndex: Int,
    val createdAt: Instant,
)

data class Checkpoint(
    val turnIndex: Int,
    val branchId: BranchId?,
)

enum class ContextStrategyType {
    SlidingWindow,
    StickyFacts,
    Branching,
}
```

### New Interfaces in Core

```kotlin
interface FactExtractor {
    suspend fun extract(history: List<Turn>, currentFacts: List<Fact>): List<Fact>
}

interface FactRepository {
    suspend fun save(sessionId: SessionId, facts: List<Fact>)
    suspend fun getBySession(sessionId: SessionId): List<Fact>
}

interface BranchRepository {
    suspend fun createBranch(sessionId: SessionId, name: String, checkpointTurnIndex: Int): BranchId
    suspend fun getBranches(sessionId: SessionId): List<Branch>
    suspend fun getBranch(branchId: BranchId): Branch?
    suspend fun deleteBranch(branchId: BranchId)
}
```

### DelegatingContextManager

```kotlin
class DelegatingContextManager(
    private val managers: Map<ContextStrategyType, ContextManager>,
) : ContextManager {
    var activeStrategy: ContextStrategyType = ContextStrategyType.SlidingWindow

    override suspend fun prepareContext(...) =
        managers.getValue(activeStrategy).prepareContext(...)
}
```

### Agent Changes

- Agent interface gets: `fun setContextStrategy(type: ContextStrategyType)`
- For branching: `suspend fun createCheckpoint(sessionId)`, `suspend fun createBranch(sessionId, name, checkpointTurnIndex)`, `suspend fun switchBranch(sessionId, branchId)`, `suspend fun listBranches(sessionId)`
- AiAgent delegates branching operations to BranchRepository

### Module Structure

- New module: `modules/data/fact-repository-exposed/`
- New module: `modules/data/fact-extractor-llm/`
- New module: `modules/data/branch-repository-exposed/`
- New module: `modules/domain/context-manager/` — add new ContextManager implementations alongside DefaultContextManager

### Trade-offs

- **Pro**: Minimal changes to existing code. ContextManager interface stays the same.
- **Con**: Branching doesn't fit cleanly into `prepareContext()` — needs separate branch management API on Agent. DelegatingContextManager adds indirection. Facts and branching logic hidden inside ContextManager implementations that have different responsibilities.

---

## Approach 2: Strategy as Agent Behavior

Strategy is a first-class behavior injected into the agent, not just context preparation.

### Architecture

```
ContextStrategy (sealed interface)
├── SlidingWindowStrategy
├── StickyFactsStrategy (owns FactStore + FactExtractor)
└── BranchingStrategy (owns BranchManager)

AiAgent
└── var contextStrategy: ContextStrategy (switchable)
```

### Core Abstractions

```kotlin
sealed interface ContextStrategy {
    val type: ContextStrategyType

    suspend fun buildMessages(
        sessionId: SessionId,
        history: List<Turn>,
        newMessage: String,
    ): List<ContextMessage>
}

interface BranchableStrategy {
    suspend fun createCheckpoint(sessionId: SessionId, turnIndex: Int): CheckpointId
    suspend fun createBranch(sessionId: SessionId, checkpointId: CheckpointId, name: String): BranchId
    suspend fun switchBranch(sessionId: SessionId, branchId: BranchId)
    suspend fun getActiveBranch(sessionId: SessionId): BranchId?
    suspend fun listBranches(sessionId: SessionId): List<Branch>
    suspend fun getBranchTree(sessionId: SessionId): BranchTree
}
```

### Strategy Implementations

**SlidingWindowStrategy:**
```kotlin
class SlidingWindowStrategy(
    private val windowSize: Int = 10,
) : ContextStrategy {
    override val type = ContextStrategyType.SlidingWindow

    override suspend fun buildMessages(sessionId, history, newMessage): List<ContextMessage> {
        val recent = history.takeLast(windowSize)
        return recent.toContextMessages() + ContextMessage(MessageRole.User, newMessage)
    }
}
```

**StickyFactsStrategy:**
```kotlin
class StickyFactsStrategy(
    private val factExtractor: FactExtractor,
    private val factRepository: FactRepository,
    private val windowSize: Int = 10,
) : ContextStrategy {
    override val type = ContextStrategyType.StickyFacts

    override suspend fun buildMessages(sessionId, history, newMessage): List<ContextMessage> {
        val currentFacts = factRepository.getBySession(sessionId)
        val updatedFacts = factExtractor.extract(history, currentFacts, newMessage)
        factRepository.save(sessionId, updatedFacts)

        val factsSystemMessage = ContextMessage(
            MessageRole.System,
            "Known facts:\n" + updatedFacts.joinToString("\n") { "${it.key}: ${it.value}" }
        )
        val recent = history.takeLast(windowSize)
        return listOf(factsSystemMessage) + recent.toContextMessages() + ContextMessage(MessageRole.User, newMessage)
    }
}
```

**BranchingStrategy:**
```kotlin
class BranchingStrategy(
    private val branchRepository: BranchRepository,
    private val turnRepository: TurnRepository,
    private val windowSize: Int = 10,
) : ContextStrategy, BranchableStrategy {
    override val type = ContextStrategyType.Branching

    override suspend fun buildMessages(sessionId, history, newMessage): List<ContextMessage> {
        val activeBranch = branchRepository.getActiveBranch(sessionId)
        val turns = if (activeBranch != null) {
            val branch = branchRepository.getBranch(activeBranch)!!
            val mainHistory = history.take(branch.checkpointTurnIndex)
            val branchTurns = turnRepository.getByBranch(activeBranch)
            mainHistory + branchTurns
        } else {
            history
        }
        val recent = turns.takeLast(windowSize)
        return recent.toContextMessages() + ContextMessage(MessageRole.User, newMessage)
    }
}
```

### Agent Interface Extensions

```kotlin
interface Agent {
    // Existing methods...

    // Strategy management
    fun getContextStrategyType(): ContextStrategyType
    fun setContextStrategy(type: ContextStrategyType)

    // Branching (only works when BranchingStrategy is active)
    suspend fun createCheckpoint(sessionId: SessionId, turnIndex: Int): Either<AgentError, CheckpointId>
    suspend fun createBranch(sessionId: SessionId, checkpointId: CheckpointId, name: String): Either<AgentError, BranchId>
    suspend fun switchBranch(sessionId: SessionId, branchId: BranchId): Either<AgentError, Unit>
    suspend fun listBranches(sessionId: SessionId): Either<AgentError, List<Branch>>
    suspend fun getBranchTree(sessionId: SessionId): Either<AgentError, BranchTree>
}
```

### BranchTree for Visual UI

```kotlin
data class BranchTree(
    val checkpoints: List<CheckpointNode>,
)

data class CheckpointNode(
    val turnIndex: Int,
    val branches: List<BranchNode>,
)

data class BranchNode(
    val id: BranchId,
    val name: String,
    val isActive: Boolean,
    val turnCount: Int,
)
```

### Module Structure

- `modules/core/` — new models (Fact, Branch, Checkpoint, BranchTree, ContextStrategyType), ContextStrategy interface, BranchableStrategy, FactExtractor, repositories
- `modules/data/fact-repository-exposed/` — FactRepository impl
- `modules/data/fact-extractor-llm/` — LLM-based FactExtractor
- `modules/data/branch-repository-exposed/` — BranchRepository impl
- `modules/domain/ai-agent/` — strategy implementations (SlidingWindowStrategy, StickyFactsStrategy, BranchingStrategy)
- `modules/presentation/compose-ui/` — strategy selector, facts panel, branch tree visualization
- `modules/presentation/app/` — DI wiring for all strategies

### Trade-offs

- **Pro**: Each strategy is self-contained with clear responsibility. Branching gets first-class API. Easy to test each strategy in isolation. Clean separation.
- **Con**: Agent interface grows. Branching methods on Agent only work with BranchingStrategy active (runtime check). More changes across layers.

---

## Approach 3: Middleware Pipeline

Context preparation as a chain of composable middleware.

### Architecture

```
ContextPipeline
├── SlidingWindowMiddleware (trim to N)
├── FactExtractionMiddleware (extract + inject facts)
└── BranchRoutingMiddleware (select branch turns)

Preset pipelines:
- SlidingWindow = [SlidingWindowMiddleware]
- StickyFacts = [FactExtractionMiddleware, SlidingWindowMiddleware]
- Branching = [BranchRoutingMiddleware, SlidingWindowMiddleware]
```

### Core Abstractions

```kotlin
data class ContextState(
    val sessionId: SessionId,
    val history: List<Turn>,
    val newMessage: String,
    val messages: List<ContextMessage> = emptyList(),
    val metadata: Map<String, Any> = emptyMap(),
)

fun interface ContextMiddleware {
    suspend fun process(state: ContextState, next: suspend (ContextState) -> ContextState): ContextState
}

class ContextPipeline(
    private val middlewares: List<ContextMiddleware>,
) {
    suspend fun execute(state: ContextState): ContextState {
        // Chain middlewares, last one builds final messages
    }
}
```

### Middleware Implementations

```kotlin
class SlidingWindowMiddleware(private val windowSize: Int) : ContextMiddleware {
    override suspend fun process(state, next): ContextState {
        val trimmed = state.copy(history = state.history.takeLast(windowSize))
        return next(trimmed)
    }
}

class FactExtractionMiddleware(
    private val factExtractor: FactExtractor,
    private val factRepository: FactRepository,
) : ContextMiddleware {
    override suspend fun process(state, next): ContextState {
        val facts = factExtractor.extract(state.history, factRepository.getBySession(state.sessionId))
        factRepository.save(state.sessionId, facts)
        val factsMessage = ContextMessage(MessageRole.System, formatFacts(facts))
        val newState = state.copy(
            messages = state.messages + factsMessage,
        )
        return next(newState)
    }
}

class BranchRoutingMiddleware(
    private val branchRepository: BranchRepository,
    private val turnRepository: TurnRepository,
) : ContextMiddleware {
    override suspend fun process(state, next): ContextState {
        val activeBranch = branchRepository.getActiveBranch(state.sessionId)
        val adjustedHistory = if (activeBranch != null) {
            // Replace history with checkpoint + branch turns
        } else state.history
        return next(state.copy(history = adjustedHistory))
    }
}
```

### Pipeline Presets

```kotlin
object ContextPipelines {
    fun slidingWindow(windowSize: Int) = ContextPipeline(
        listOf(SlidingWindowMiddleware(windowSize), MessageBuilderMiddleware())
    )
    fun stickyFacts(factExtractor, factRepo, windowSize) = ContextPipeline(
        listOf(FactExtractionMiddleware(factExtractor, factRepo), SlidingWindowMiddleware(windowSize), MessageBuilderMiddleware())
    )
    fun branching(branchRepo, turnRepo, windowSize) = ContextPipeline(
        listOf(BranchRoutingMiddleware(branchRepo, turnRepo), SlidingWindowMiddleware(windowSize), MessageBuilderMiddleware())
    )
}
```

### Agent Integration

- ContextManager replaced by ContextPipeline
- Agent holds `var activePipeline: ContextPipeline`
- Branching operations still need separate API (pipeline handles context prep only)

### Module Structure

- `modules/core/` — ContextMiddleware, ContextState, ContextPipeline interfaces, new models
- `modules/domain/context-pipeline/` — middleware implementations, preset pipelines
- `modules/data/fact-repository-exposed/`, `branch-repository-exposed/`, `fact-extractor-llm/` — same as Approach 2
- `modules/presentation/compose-ui/` — same UI requirements
- `modules/presentation/app/` — DI wiring

### Trade-offs

- **Pro**: Maximum composability. SlidingWindow reused in all presets. Easy to add new middleware. Clean separation of concerns.
- **Con**: Over-engineering for 3 strategies. Pipeline chain adds complexity. Branching still needs out-of-band API for checkpoint/branch management — pipeline only handles context prep. ContextState metadata is untyped (Map<String, Any>).

---

## Common: Shared Across All Approaches

### New Core Models (all approaches need these)

- `Fact(key, value, updatedAt)` + `FactRepository`
- `Branch(id, sessionId, name, checkpointTurnIndex, createdAt)` + `BranchRepository`
- `BranchId` value class
- `CheckpointId` value class
- `ContextStrategyType` enum
- `BranchTree`, `CheckpointNode`, `BranchNode` for visual UI

### New Data Modules (all approaches need these)

- `fact-repository-exposed/` — Exposed + SQLite
- `fact-extractor-llm/` — LLM-based fact extraction via OpenRouterService
- `branch-repository-exposed/` — Exposed + SQLite, branch turns storage

### FactExtractor LLM Prompt

System prompt for fact extraction:
```
You are a fact extractor. Given a conversation and existing facts, extract/update key-value facts.
Return JSON: {"facts": [{"key": "...", "value": "..."}, ...]}
Categories: goal, constraints, preferences, decisions, agreements, requirements.
Only include facts explicitly stated or clearly implied. Remove facts that were contradicted.
```

### UI Components (all approaches need these)

- `StrategySelector` — dropdown to pick active strategy
- `FactsPanel` — shows current facts (visible when StickyFacts active)
- `BranchTreeView` — visual tree of checkpoints and branches (visible when Branching active)
- `BranchControls` — create checkpoint, create branch, switch branch buttons

### Integration Test

Single test class `ContextStrategyComparisonTest`:
- Predefined scenario: 12 messages simulating requirements gathering
- MockEngine with deterministic LLM responses
- Runs scenario through each strategy
- Collects metrics: total tokens (prompt + completion), LLM call count, facts retained, context message count per turn
- Outputs comparison table
- Asserts baseline expectations (e.g., SlidingWindow uses fewest tokens)
