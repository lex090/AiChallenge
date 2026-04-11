# Strict DDD Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor AI Agent Chat to strict DDD with proper bounded contexts, aggregates, domain events, application services, ACL, and comprehensive KDoc documentation.

**Architecture:** Two bounded contexts (Conversation, Context Management) connected through domain events and ports. Application services layer orchestrates use cases. LlmPort ACL isolates domain from infrastructure. Strategy pattern replaces DefaultContextManager god-class.

**Tech Stack:** Kotlin 2.3.20, Arrow 2.1.2 (Either), Ktor 3.4.2, Exposed 0.61.0, Decompose 3.5.0, MVIKotlin 4.3.0, Koin 4.1.0

**Spec:** `docs/superpowers/specs/2026-04-11-ddd-refactoring-design.md`

**Branch:** `feature/strict-ddd-refactoring`

---

## Project Rules (from CLAUDE.md)

- **No default parameter values** — all arguments explicit at every call site
- **Named arguments** at every call site
- **All dependencies through Gradle Version Catalog** (`gradle/libs.versions.toml`)
- **Repository naming:** `{DomainModel}Repository`
- **Error handling:** Arrow `Either<DomainError, T>` at domain boundaries
- **Testing:** `kotlin-test`, Ktor `MockEngine`, `Dispatchers.setMain(StandardTestDispatcher())`

---

## File Map

### New files to create

**Core module (ports, events, shared):**
- `modules/core/src/main/kotlin/com/ai/challenge/core/llm/LlmPort.kt`
- `modules/core/src/main/kotlin/com/ai/challenge/core/llm/LlmResponse.kt`
- `modules/core/src/main/kotlin/com/ai/challenge/core/llm/ResponseFormat.kt`
- `modules/core/src/main/kotlin/com/ai/challenge/core/event/DomainEventPublisher.kt`
- `modules/core/src/main/kotlin/com/ai/challenge/core/event/DomainEventHandler.kt`
- `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextPreparationPort.kt`
- `modules/core/src/main/kotlin/com/ai/challenge/core/context/TurnReader.kt`
- `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextStrategyConfig.kt`

**Data module (ACL adapter):**
- `modules/data/open-router-service/src/main/kotlin/com/ai/challenge/llm/OpenRouterAdapter.kt`

**Domain module (strategies):**
- `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/ContextStrategy.kt`
- `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/ContextPreparationService.kt`
- `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/PassthroughStrategy.kt`
- `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/SlidingWindowStrategy.kt`
- `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/SummarizeOnThresholdStrategy.kt`
- `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/StickyFactsStrategy.kt`

**Domain module (event handlers):**
- `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/SessionDeletedCleanupHandler.kt`

**Domain module (TurnReader impl):**
- `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AgentSessionTurnReader.kt`

**Application services:**
- `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/usecase/SendMessageUseCase.kt`
- `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/usecase/CreateSessionUseCase.kt`
- `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/usecase/DeleteSessionUseCase.kt`
- `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/usecase/ApplicationInitService.kt`
- `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/usecase/SwitchBranchUseCase.kt`
- `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/event/InProcessDomainEventPublisher.kt`

**Tests:**
- `modules/core/src/test/kotlin/com/ai/challenge/core/session/AgentSessionTest.kt`
- `modules/core/src/test/kotlin/com/ai/challenge/core/context/ContextStrategyConfigTest.kt`
- `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/PassthroughStrategyTest.kt`
- `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/SlidingWindowStrategyTest.kt`
- `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/SummarizeOnThresholdStrategyTest.kt`
- `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/StickyFactsStrategyTest.kt`
- `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/SessionDeletedCleanupHandlerTest.kt`
- `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/ContextPreparationServiceTest.kt`
- `modules/presentation/app/src/test/kotlin/com/ai/challenge/app/event/InProcessDomainEventPublisherTest.kt`
- `modules/presentation/app/src/test/kotlin/com/ai/challenge/app/usecase/SendMessageUseCaseTest.kt`
- `modules/presentation/app/src/test/kotlin/com/ai/challenge/app/usecase/CreateSessionUseCaseTest.kt`
- `modules/presentation/app/src/test/kotlin/com/ai/challenge/app/usecase/DeleteSessionUseCaseTest.kt`

### Files to modify

**Core module (KDoc + reclassification):**
- `modules/core/src/main/kotlin/com/ai/challenge/core/session/AgentSession.kt` — KDoc, add `ensureBranchDeletable()`
- `modules/core/src/main/kotlin/com/ai/challenge/core/branch/Branch.kt` — KDoc, reclassify to Entity, remove `ensureDeletable()`
- `modules/core/src/main/kotlin/com/ai/challenge/core/turn/Turn.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/session/AgentSessionId.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchId.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/turn/TurnId.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/chat/model/SessionTitle.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/chat/model/MessageContent.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/branch/TurnSequence.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/usage/model/UsageRecord.kt` — KDoc, add ZERO constant
- `modules/core/src/main/kotlin/com/ai/challenge/core/usage/model/TokenCount.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/usage/model/Cost.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/shared/CreatedAt.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/shared/UpdatedAt.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementType.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/error/DomainError.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/event/DomainEvent.kt` — KDoc, add branchId to TurnRecorded
- `modules/core/src/main/kotlin/com/ai/challenge/core/chat/AgentSessionRepository.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/chat/ChatService.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/chat/SessionService.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/chat/BranchService.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/usage/UsageService.kt` — KDoc, rename to UsageQueryService
- `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManager.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/fact/Fact.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactCategory.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactRepository.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/summary/Summary.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/summary/SummaryRepository.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextMessage.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/context/PreparedContext.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/context/MessageRole.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/context/model/FactKey.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/context/model/FactValue.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/context/model/SummaryContent.kt` — KDoc
- `modules/core/src/main/kotlin/com/ai/challenge/core/context/model/TurnIndex.kt` — KDoc

**Data module (adapter, build config):**
- `modules/data/open-router-service/build.gradle.kts` — add dependency on `:modules:core`

**Domain module (use LlmPort):**
- `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiChatService.kt` — use LlmPort instead of OpenRouterService
- `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiBranchService.kt` — use AgentSession.ensureBranchDeletable()
- `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiUsageService.kt` — use UsageRecord.ZERO, rename to AiUsageQueryService
- `modules/domain/ai-agent/build.gradle.kts` — remove direct open-router-service dependency
- `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/LlmContextCompressor.kt` — use LlmPort
- `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/LlmFactExtractor.kt` — use LlmPort
- `modules/domain/context-manager/build.gradle.kts` — remove direct open-router-service dependency

**Presentation module:**
- `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt` — thin out, use Application Services
- `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/sessionlist/store/SessionListStoreFactory.kt` — thin out
- `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootComponent.kt` — remove business logic
- `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/Main.kt` — use ApplicationInitService
- `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt` — wire new components

---

## Phase 1: Foundation — KDoc, Branch Reclassification, UsageRecord.ZERO

No behavior changes. Purely additive: documentation + moving one invariant check.

### Task 1: KDoc for Aggregate Root, Entities, Typed IDs

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/session/AgentSession.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/branch/Branch.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/turn/Turn.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/session/AgentSessionId.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchId.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/turn/TurnId.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/branch/TurnSequence.kt`

- [ ] **Step 1: Add KDoc to AgentSession**

Replace the existing comment block on AgentSession with:

```kotlin
/**
 * Aggregate Root — root of the "Conversation" aggregate.
 *
 * Represents one conversation between user and AI agent.
 * Single entry point for all operations within a session:
 * creating branches, appending turns.
 *
 * Transactional boundary: all changes to [Branch] and [Turn]
 * go through this aggregate and are saved atomically.
 *
 * Invariants:
 * - Always has exactly one main [Branch] ([Branch.sourceTurnId] == null)
 * - [Branch] can only be created if [contextManagementType] == [ContextManagementType.Branching]
 * - Main [Branch] cannot be deleted
 * - [title] is auto-generated from first message if empty
 *
 * Child entities: [Branch], [Turn]
 */
```

- [ ] **Step 2: Reclassify Branch — update KDoc from Aggregate Root to Entity**

Replace the existing comment block on Branch with:

```kotlin
/**
 * Entity — conversation branch within aggregate [AgentSession].
 *
 * Has stable identity [BranchId], but is NOT an independent
 * Aggregate Root — access only through [AgentSessionRepository].
 *
 * Lifecycle: main branch created with session ([sourceTurnId] == null).
 * Additional branches created when user branches from an existing [Turn].
 * Deleted cascadingly when session is deleted.
 *
 * Invariants:
 * - [isMain] == true when [sourceTurnId] == null
 * - [turnSequence] is ordered chronologically and append-only
 * - [sourceTurnId] references an existing [Turn] in the parent branch
 *
 * Not a separate Aggregate Root because:
 * - Cannot exist without session
 * - "Main branch not deletable" is an [AgentSession] aggregate invariant
 * - All operations go through [AgentSessionRepository] (one repo per aggregate)
 */
```

- [ ] **Step 3: Add KDoc to Turn**

Replace the existing comment block on Turn with:

```kotlin
/**
 * Entity — single exchange (user message + assistant response)
 * within aggregate [AgentSession]. Immutable.
 *
 * Has stable identity [TurnId] — branches reference turns,
 * UI queries metrics by turn. Two turns with identical text
 * are different turns (entity semantics, not value semantics).
 *
 * Created once during request processing and never modified (write-once).
 *
 * Lifecycle: created when user sends a message and receives a response.
 * Deleted cascadingly when session is deleted.
 *
 * [usage] — embedded [UsageRecord] value object with token/cost metrics.
 * Part of Turn because it is created simultaneously, never changes,
 * and has no independent lifecycle or identity.
 *
 * [sessionId] — reference to parent aggregate root. Deliberate compromise
 * for query-performance in relational DB. In strict DDD, child entities
 * don't store root ID, but here it simplifies repository queries.
 */
```

- [ ] **Step 4: Add KDoc to typed IDs (AgentSessionId, BranchId, TurnId)**

Add before each ID class:

AgentSessionId:
```kotlin
/**
 * Typed identifier for aggregate [AgentSession].
 *
 * Value class over String (UUID). Ensures type safety —
 * impossible to accidentally pass [BranchId] or [TurnId]
 * where [AgentSessionId] is expected.
 *
 * Generation: [generate] creates a new unique identifier.
 */
```

BranchId:
```kotlin
/**
 * Typed identifier for entity [Branch].
 *
 * Value class over String (UUID). Ensures type safety —
 * impossible to accidentally pass [AgentSessionId] or [TurnId]
 * where [BranchId] is expected.
 *
 * Generation: [generate] creates a new unique identifier.
 */
```

TurnId:
```kotlin
/**
 * Typed identifier for entity [Turn].
 *
 * Value class over String (UUID). Ensures type safety —
 * impossible to accidentally pass [AgentSessionId] or [BranchId]
 * where [TurnId] is expected.
 *
 * Generation: [generate] creates a new unique identifier.
 */
```

- [ ] **Step 5: Add KDoc to TurnSequence**

```kotlin
/**
 * Value Object — ordered sequence of [TurnId] references within a [Branch].
 *
 * Has no identity — defined only by the list of IDs it contains.
 * Immutable. Append-only in practice (new turns appended at the end).
 *
 * [trunkUpTo] creates a subsequence for branch creation —
 * the new branch inherits history up to the branching point.
 *
 * Embedded in [Branch]. Does not store [Turn] objects directly —
 * only ID-based references to avoid object graph complexity.
 */
```

- [ ] **Step 6: Run tests to verify no breakage**

Run: `./gradlew :modules:core:test`
Expected: All tests pass (KDoc changes only, no behavior change)

- [ ] **Step 7: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/session/AgentSession.kt \
  modules/core/src/main/kotlin/com/ai/challenge/core/branch/Branch.kt \
  modules/core/src/main/kotlin/com/ai/challenge/core/turn/Turn.kt \
  modules/core/src/main/kotlin/com/ai/challenge/core/session/AgentSessionId.kt \
  modules/core/src/main/kotlin/com/ai/challenge/core/branch/BranchId.kt \
  modules/core/src/main/kotlin/com/ai/challenge/core/turn/TurnId.kt \
  modules/core/src/main/kotlin/com/ai/challenge/core/branch/TurnSequence.kt
git commit -m "docs: add DDD KDoc to aggregate root, entities, and typed IDs"
```

---

### Task 2: KDoc for Value Objects

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/chat/model/SessionTitle.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/chat/model/MessageContent.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/usage/model/UsageRecord.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/usage/model/TokenCount.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/usage/model/Cost.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/shared/CreatedAt.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/shared/UpdatedAt.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementType.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextMessage.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/context/PreparedContext.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/context/MessageRole.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/context/model/FactKey.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/context/model/FactValue.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/context/model/SummaryContent.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/context/model/TurnIndex.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/Fact.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactCategory.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/summary/Summary.kt`

- [ ] **Step 1: Add KDoc to SessionTitle and MessageContent**

SessionTitle:
```kotlin
/**
 * Value Object — title of an [AgentSession].
 *
 * Has no identity — defined only by the string it wraps.
 * Immutable. Can be empty at session creation (auto-generated
 * from first user message by [SendMessageUseCase]).
 */
```

MessageContent:
```kotlin
/**
 * Value Object — text content of a message (user or assistant).
 *
 * Has no identity — defined only by the string it wraps.
 * Immutable. Single type for both user and assistant messages,
 * making function signatures self-documenting:
 * `send(message: MessageContent)` vs `send(message: String)`.
 */
```

- [ ] **Step 2: Update UsageRecord KDoc (already has KDoc, enhance it)**

Replace the existing comment block on UsageRecord with:

```kotlin
/**
 * Value Object — usage metrics for a single [Turn].
 *
 * Has no identity — defined only by its attributes.
 * Immutable. Created once with [Turn] and never modified.
 * Two UsageRecords with identical fields are fully interchangeable.
 *
 * Supports aggregation through [plus] operator for computing
 * session-level totals via [UsageQueryService].
 *
 * Preserves full granularity from OpenRouter API:
 * 5 token fields (prompt, completion, cached, cacheWrite, reasoning)
 * 4 cost fields (total, upstream, upstreamPrompt, upstreamCompletion)
 *
 * Embedded in [Turn] as a composite part — not stored separately.
 */
```

- [ ] **Step 3: Add KDoc to TokenCount and Cost**

TokenCount:
```kotlin
/**
 * Value Object — count of tokens consumed by an LLM operation.
 *
 * Has no identity — defined only by the integer it wraps.
 * Immutable. Supports arithmetic via [plus] for session-level aggregation.
 * Cannot be negative (domain invariant).
 */
```

Cost:
```kotlin
/**
 * Value Object — monetary cost of an LLM operation.
 *
 * Uses [BigDecimal] (not Double) because Double is unsuitable
 * for monetary calculations due to floating-point precision loss
 * (0.1 + 0.2 != 0.3 in IEEE 754).
 *
 * Has no identity. Immutable. Supports arithmetic via [plus].
 */
```

- [ ] **Step 4: Add KDoc to CreatedAt and UpdatedAt**

CreatedAt:
```kotlin
/**
 * Value Object — moment of entity creation.
 *
 * Set once at creation, never changes. Separated from [UpdatedAt]
 * to express different domain semantics — creation is an immutable fact,
 * update is a mutable timestamp that changes on each mutation.
 */
```

UpdatedAt:
```kotlin
/**
 * Value Object — moment of last aggregate mutation.
 *
 * Changes on each mutation of the aggregate root.
 * Only present on mutable entities ([AgentSession]).
 * Immutable entities ([Turn]) have only [CreatedAt].
 */
```

- [ ] **Step 5: Add KDoc to ContextManagementType**

```kotlin
/**
 * Value Object — sealed hierarchy of context management strategies.
 *
 * Determines how conversation context is prepared before
 * each LLM call. Stored as attribute of [AgentSession].
 *
 * Each variant corresponds to a [ContextStrategy] implementation
 * and a [ContextStrategyConfig] configuration.
 *
 * [None] — full history, no processing.
 * [SummarizeOnThreshold] — compress old turns when history exceeds threshold.
 * [SlidingWindow] — keep only last N turns.
 * [StickyFacts] — extract facts via LLM, retain with recent turns.
 * [Branching] — passthrough for current branch history.
 */
```

- [ ] **Step 6: Add KDoc to ContextMessage, PreparedContext, MessageRole**

ContextMessage:
```kotlin
/**
 * Value Object — a single message in the prepared LLM context.
 *
 * Building block for [PreparedContext]. Pairs [MessageRole]
 * with [MessageContent] to represent system prompts,
 * user messages, and assistant responses.
 */
```

PreparedContext:
```kotlin
/**
 * Value Object — result of context preparation for an LLM call.
 *
 * Output of [ContextPreparationPort]. Contains the ordered
 * list of [ContextMessage] ready to send to LLM, plus metadata
 * about compression applied.
 *
 * Immutable. Created once per message send cycle.
 */
```

MessageRole:
```kotlin
/**
 * Value Object — role of a message in LLM context.
 *
 * [System] — system prompt (instructions, facts, summaries).
 * [User] — user's message.
 * [Assistant] — LLM's response.
 */
```

- [ ] **Step 7: Add KDoc to context model value objects**

FactKey:
```kotlin
/**
 * Value Object — key in a [Fact] key-value pair.
 * Identifies what the fact is about (e.g., "preferred language").
 */
```

FactValue:
```kotlin
/**
 * Value Object — value in a [Fact] key-value pair.
 * The actual content of the extracted fact.
 */
```

SummaryContent:
```kotlin
/**
 * Value Object — text content of a conversation [Summary].
 * Contains the compressed representation of a range of turns.
 */
```

TurnIndex:
```kotlin
/**
 * Value Object — zero-based index of a [Turn] in a session's history.
 * Used by [Summary] to define the range of summarized turns.
 */
```

- [ ] **Step 8: Add KDoc to Fact, FactCategory, Summary**

Fact:
```kotlin
/**
 * Value Object — extracted fact from conversation.
 *
 * Has no identity — facts are fully recreated on each message
 * (replace-all semantics in [FactRepository]).
 * Defined only by [category] + [key] + [value].
 *
 * Not part of [AgentSession] aggregate because:
 * - AgentSession doesn't know about facts
 * - Facts are internal state of [StickyFactsStrategy]
 * - Different lifecycle (replace-all vs append-only for turns)
 * - Stored in separate database (facts.db)
 *
 * [sessionId] is a correlation ID, not aggregate membership.
 */
```

FactCategory:
```kotlin
/**
 * Value Object — classification of an extracted [Fact].
 *
 * Used by [LlmFactExtractor] to categorize facts
 * extracted from conversation via LLM.
 */
```

Summary:
```kotlin
/**
 * Value Object — summarization result for a range of turns.
 *
 * Has no identity — write-once, never updated.
 * Defined by [content] + turn range ([fromTurnIndex]..[toTurnIndex]).
 *
 * Not part of [AgentSession] aggregate — internal state of
 * [SummarizeOnThresholdStrategy] in Context Management context.
 *
 * [sessionId] is a correlation ID, not aggregate membership.
 */
```

- [ ] **Step 9: Run tests**

Run: `./gradlew :modules:core:test`
Expected: All tests pass

- [ ] **Step 10: Commit**

```bash
git add modules/core/src/main/kotlin/
git commit -m "docs: add DDD KDoc to all value objects in core module"
```

---

### Task 3: KDoc for Repository Interfaces, Service Interfaces, Domain Events, Errors

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/chat/AgentSessionRepository.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/chat/ChatService.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/chat/SessionService.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/chat/BranchService.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/usage/UsageService.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManager.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/fact/FactRepository.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/summary/SummaryRepository.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/event/DomainEvent.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/error/DomainError.kt`

- [ ] **Step 1: Add KDoc to AgentSessionRepository**

```kotlin
/**
 * Repository — sole access point to the [AgentSession] aggregate
 * and its child entities [Branch] and [Turn].
 *
 * DDD rule: one repository per aggregate. All operations with
 * child entities go through this interface, not through separate
 * repositories. This guarantees aggregate invariants are checked
 * in one place.
 *
 * Implementation may internally use separate tables
 * (sessions, branches, turns, branch_turns), but the external API
 * operates only on domain models.
 */
```

- [ ] **Step 2: Add KDoc to service interfaces**

ChatService:
```kotlin
/**
 * Domain Service — sending messages to AI agent.
 *
 * Orchestrates: context preparation (via [ContextPreparationPort]),
 * LLM call (via [LlmPort]), [Turn] creation and persistence
 * (via [AgentSessionRepository]).
 *
 * Contains no own state — all logic is stateless.
 */
```

SessionService:
```kotlin
/**
 * Domain Service — [AgentSession] lifecycle management.
 *
 * CRUD operations on sessions. Updates title and
 * [ContextManagementType]. Does not manage branches or turns
 * directly — that is [BranchService] and [ChatService] responsibility.
 *
 * Contains no own state — all logic is stateless.
 */
```

BranchService:
```kotlin
/**
 * Domain Service — [Branch] management within an [AgentSession].
 *
 * Creates branches from existing turns, deletes non-main branches,
 * retrieves branch lists and turns. Validates aggregate invariants:
 * branching must be enabled, main branch cannot be deleted.
 *
 * Contains no own state — all logic is stateless.
 */
```

UsageService (to be renamed to UsageQueryService in a later task):
```kotlin
/**
 * Domain Service — read-only usage metrics aggregation.
 *
 * Queries [Turn] data from [AgentSessionRepository] and aggregates
 * [UsageRecord] by turn, session, or session total.
 *
 * Read-only service — does not mutate any data.
 * Contains no own state — all logic is stateless.
 */
```

ContextManager:
```kotlin
/**
 * Port — context preparation for Conversation Context.
 *
 * Called before each LLM message send to prepare the conversation
 * context according to the session's [ContextManagementType].
 *
 * Implemented in Context Management bounded context
 * ([ContextPreparationService]).
 *
 * Dependency direction: defined in core (Conversation Context),
 * implemented in context-manager module (Context Management Context).
 */
```

- [ ] **Step 3: Add KDoc to FactRepository and SummaryRepository**

FactRepository:
```kotlin
/**
 * Repository — persistence for [Fact] value objects
 * in Context Management bounded context.
 *
 * Not part of [AgentSession] aggregate — internal state of
 * [StickyFactsStrategy]. Accessed only by context management services.
 *
 * [save] implements replace-all semantics: deletes all session facts
 * and writes new ones. This is correct because [Fact] is a value object
 * without stable identity — facts are fully recreated on each message.
 */
```

SummaryRepository:
```kotlin
/**
 * Repository — persistence for [Summary] value objects
 * in Context Management bounded context.
 *
 * Not part of [AgentSession] aggregate — internal state of
 * [SummarizeOnThresholdStrategy]. Accessed only by context management services.
 *
 * Append-only: [save] inserts a new summary, never replaces existing ones.
 */
```

- [ ] **Step 4: Update DomainEvent KDoc and add branchId to TurnRecorded**

```kotlin
/**
 * Domain Event — fact of a change that occurred in the domain.
 *
 * Events are immutable and represent a completed action (past tense).
 * Used for communication between Bounded Contexts
 * (Conversation -> Context Management) without creating
 * direct dependencies.
 *
 * All events contain [sessionId] as correlation identifier,
 * allowing Context Management context to identify affected data.
 */
sealed interface DomainEvent {
    val sessionId: AgentSessionId
}

/**
 * Domain Event — a new [Turn] was recorded in a session.
 *
 * Published from [ChatService] after successful Turn save.
 *
 * Subscribers:
 * - Context Management: may trigger incremental fact extraction
 *   or summary update depending on active strategy.
 */
data class TurnRecorded(
    override val sessionId: AgentSessionId,
    val turn: Turn,
    val branchId: BranchId,
) : DomainEvent

/**
 * Domain Event — a new [AgentSession] was created.
 *
 * Published from [CreateSessionUseCase] after session + main branch creation.
 *
 * Subscribers: none currently (extensibility point).
 */
data class SessionCreated(
    override val sessionId: AgentSessionId,
) : DomainEvent

/**
 * Domain Event — an [AgentSession] was deleted.
 *
 * Published from [DeleteSessionUseCase] after aggregate deletion.
 *
 * Subscribers:
 * - Context Management: [SessionDeletedCleanupHandler] deletes
 *   orphaned Facts and Summaries for this session.
 */
data class SessionDeleted(
    override val sessionId: AgentSessionId,
) : DomainEvent
```

- [ ] **Step 5: Add KDoc to DomainError**

```kotlin
/**
 * Value Object — sealed hierarchy of domain errors.
 *
 * Used with Arrow [Either] at domain boundaries:
 * `Either<DomainError, T>`. Presentation layer pattern-matches
 * on error type — no try/catch.
 *
 * Infrastructure errors: [NetworkError], [ApiError], [DatabaseError]
 * Resource errors: [SessionNotFound], [BranchNotFound], [TurnNotFound]
 * Business rule violations: [MainBranchCannotBeDeleted],
 *   [BranchingNotEnabled], [BranchNotOwnedBySession]
 */
```

- [ ] **Step 6: Run tests**

Run: `./gradlew :modules:core:test`
Expected: All tests pass

- [ ] **Step 7: Commit**

```bash
git add modules/core/src/main/kotlin/
git commit -m "docs: add DDD KDoc to repositories, services, events, and errors"
```

---

### Task 4: Move ensureBranchDeletable to AgentSession, add UsageRecord.ZERO

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/session/AgentSession.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/branch/Branch.kt`
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/usage/model/UsageRecord.kt`
- Modify: `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiBranchService.kt`
- Modify: `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiUsageService.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt`
- Create: `modules/core/src/test/kotlin/com/ai/challenge/core/session/AgentSessionTest.kt`

- [ ] **Step 1: Write test for AgentSession.ensureBranchDeletable**

Create `modules/core/src/test/kotlin/com/ai/challenge/core/session/AgentSessionTest.kt`:

```kotlin
package com.ai.challenge.core.session

import arrow.core.Either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.TurnSequence
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.shared.UpdatedAt
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertIs

class AgentSessionTest {

    private val now = Clock.System.now()

    private val session = AgentSession(
        id = AgentSessionId.generate(),
        title = SessionTitle(value = "Test"),
        contextManagementType = ContextManagementType.None,
        createdAt = CreatedAt(value = now),
        updatedAt = UpdatedAt(value = now),
    )

    @Test
    fun `ensureBranchDeletable returns Left for main branch`() {
        val mainBranch = Branch(
            id = BranchId.generate(),
            sessionId = session.id,
            sourceTurnId = null,
            turnSequence = TurnSequence(value = emptyList()),
            createdAt = CreatedAt(value = now),
        )

        val result = session.ensureBranchDeletable(branch = mainBranch)

        assertIs<Either.Left<DomainError.MainBranchCannotBeDeleted>>(value = result)
    }

    @Test
    fun `ensureBranchDeletable returns Right for non-main branch`() {
        val nonMainBranch = Branch(
            id = BranchId.generate(),
            sessionId = session.id,
            sourceTurnId = com.ai.challenge.core.turn.TurnId.generate(),
            turnSequence = TurnSequence(value = emptyList()),
            createdAt = CreatedAt(value = now),
        )

        val result = session.ensureBranchDeletable(branch = nonMainBranch)

        assertIs<Either.Right<Unit>>(value = result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :modules:core:test --tests "com.ai.challenge.core.session.AgentSessionTest"`
Expected: FAIL — `ensureBranchDeletable` does not exist on AgentSession

- [ ] **Step 3: Add ensureBranchDeletable to AgentSession**

Add to `AgentSession.kt` after `withContextManagementType`:

```kotlin
    fun ensureBranchDeletable(branch: Branch): Either<DomainError, Unit> =
        if (branch.isMain) {
            Either.Left(value = DomainError.MainBranchCannotBeDeleted(sessionId = id))
        } else {
            Either.Right(value = Unit)
        }
```

Add necessary imports: `import com.ai.challenge.core.branch.Branch`, `import com.ai.challenge.core.error.DomainError`, `import arrow.core.Either`.

- [ ] **Step 4: Remove ensureDeletable from Branch**

In `Branch.kt`, remove the `ensureDeletable()` method entirely. Keep `isMain` property — it's still useful as a computed property.

- [ ] **Step 5: Update AiBranchService to use AgentSession.ensureBranchDeletable**

In `AiBranchService.kt`, in the `delete` method, change:

```kotlin
// Before:
branch.ensureDeletable().bind()

// After:
val session = repository.get(id = branch.sessionId)
    ?: return@either raise(DomainError.SessionNotFound(id = branch.sessionId))
session.ensureBranchDeletable(branch = branch).bind()
```

Note: Check the exact current code — the session may already be loaded. Adjust accordingly to avoid duplicate loads.

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :modules:core:test --tests "com.ai.challenge.core.session.AgentSessionTest"`
Expected: PASS

- [ ] **Step 7: Add UsageRecord.ZERO companion constant**

In `UsageRecord.kt`, add a companion object inside the data class:

```kotlin
    companion object {
        val ZERO: UsageRecord = UsageRecord(
            promptTokens = TokenCount(value = 0),
            completionTokens = TokenCount(value = 0),
            cachedTokens = TokenCount(value = 0),
            cacheWriteTokens = TokenCount(value = 0),
            reasoningTokens = TokenCount(value = 0),
            totalCost = Cost(value = java.math.BigDecimal.ZERO),
            upstreamCost = Cost(value = java.math.BigDecimal.ZERO),
            upstreamPromptCost = Cost(value = java.math.BigDecimal.ZERO),
            upstreamCompletionsCost = Cost(value = java.math.BigDecimal.ZERO),
        )
    }
```

- [ ] **Step 8: Replace duplicated empty usage records**

In `AiUsageService.kt`, replace the local `emptyUsageRecord()` function with `UsageRecord.ZERO`.

In `ChatStoreFactory.kt`, replace the local `emptyUsageRecord()` function and all its call sites with `UsageRecord.ZERO`.

- [ ] **Step 9: Run full tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "refactor: move ensureBranchDeletable to AgentSession, add UsageRecord.ZERO"
```

---

## Phase 2: Anti-Corruption Layer — LlmPort

### Task 5: Create LlmPort, LlmResponse, ResponseFormat in core

**Files:**
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/llm/LlmPort.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/llm/LlmResponse.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/llm/ResponseFormat.kt`

- [ ] **Step 1: Create ResponseFormat**

Create `modules/core/src/main/kotlin/com/ai/challenge/core/llm/ResponseFormat.kt`:

```kotlin
package com.ai.challenge.core.llm

/**
 * Value Object — requested format for LLM response.
 *
 * Used by [LlmPort] to request structured output
 * (JSON for fact extraction via [StickyFactsStrategy]).
 *
 * [Text] — free-form text response (default).
 * [Json] — JSON-structured response.
 */
sealed interface ResponseFormat {
    data object Text : ResponseFormat
    data object Json : ResponseFormat
}
```

- [ ] **Step 2: Create LlmResponse**

Create `modules/core/src/main/kotlin/com/ai/challenge/core/llm/LlmResponse.kt`:

```kotlin
package com.ai.challenge.core.llm

import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.usage.model.UsageRecord

/**
 * Value Object — LLM response translated to domain types.
 *
 * Does not contain provider-specific details (OpenRouter, Anthropic).
 * Mapping from provider models happens in the [LlmPort] adapter
 * (Anti-Corruption Layer).
 */
data class LlmResponse(
    val content: MessageContent,
    val usage: UsageRecord,
)
```

- [ ] **Step 3: Create LlmPort**

Create `modules/core/src/main/kotlin/com/ai/challenge/core/llm/LlmPort.kt`:

```kotlin
package com.ai.challenge.core.llm

import arrow.core.Either
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.error.DomainError

/**
 * Port — abstraction for LLM access from domain layer.
 *
 * Anti-Corruption Layer by Evans: domain services work
 * with domain types ([ContextMessage], [LlmResponse]),
 * not with external API models (ChatRequest, ChatResponse).
 *
 * Implemented in infrastructure layer ([OpenRouterAdapter]).
 * Allows replacing LLM provider without changing domain.
 *
 * Dependency direction: defined in core (domain),
 * implemented in data/open-router-service (infrastructure).
 */
interface LlmPort {
    suspend fun complete(
        messages: List<ContextMessage>,
        responseFormat: ResponseFormat,
    ): Either<DomainError, LlmResponse>
}
```

- [ ] **Step 4: Run core module compilation**

Run: `./gradlew :modules:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/llm/
git commit -m "feat: create LlmPort, LlmResponse, ResponseFormat in core"
```

---

### Task 6: Create OpenRouterAdapter implementing LlmPort

**Files:**
- Create: `modules/data/open-router-service/src/main/kotlin/com/ai/challenge/llm/OpenRouterAdapter.kt`
- Modify: `modules/data/open-router-service/build.gradle.kts`

- [ ] **Step 1: Add core dependency to open-router-service**

In `modules/data/open-router-service/build.gradle.kts`, add to dependencies:

```kotlin
implementation(project(":modules:core"))
implementation(libs.arrow.core)
```

- [ ] **Step 2: Create OpenRouterAdapter**

Create `modules/data/open-router-service/src/main/kotlin/com/ai/challenge/llm/OpenRouterAdapter.kt`:

```kotlin
package com.ai.challenge.llm

import arrow.core.Either
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.llm.LlmPort
import com.ai.challenge.core.llm.LlmResponse
import com.ai.challenge.core.llm.ResponseFormat
import com.ai.challenge.core.usage.model.Cost
import com.ai.challenge.core.usage.model.TokenCount
import com.ai.challenge.core.usage.model.UsageRecord
import com.ai.challenge.llm.model.ChatResponse
import java.math.BigDecimal

/**
 * Anti-Corruption Layer — adapter between domain [LlmPort]
 * and infrastructure [OpenRouterService].
 *
 * Responsibilities:
 * - Translates [ContextMessage] -> ChatRequest (via [ChatScope] DSL)
 * - Translates [ChatResponse] -> [LlmResponse]
 * - Maps Double cost -> BigDecimal [Cost]
 * - Maps exceptions -> [DomainError.NetworkError] / [DomainError.ApiError]
 *
 * Domain layer has no knowledge of OpenRouter.
 */
class OpenRouterAdapter(
    private val openRouterService: OpenRouterService,
    private val model: String,
) : LlmPort {

    override suspend fun complete(
        messages: List<ContextMessage>,
        responseFormat: ResponseFormat,
    ): Either<DomainError, LlmResponse> =
        try {
            val response = openRouterService.chat(model = model) {
                messages.forEach { msg ->
                    when (msg.role) {
                        MessageRole.System -> system(content = msg.content.value)
                        MessageRole.User -> user(content = msg.content.value)
                        MessageRole.Assistant -> assistant(content = msg.content.value)
                    }
                }
                when (responseFormat) {
                    ResponseFormat.Json -> jsonMode()
                    ResponseFormat.Text -> {}
                }
            }

            val errorMessage = response.choices.firstOrNull()?.error?.message
            if (errorMessage != null) {
                Either.Left(value = DomainError.ApiError(message = errorMessage))
            } else {
                val content = response.choices.firstOrNull()?.message?.content.orEmpty()
                Either.Right(
                    value = LlmResponse(
                        content = MessageContent(value = content),
                        usage = mapUsage(response = response),
                    )
                )
            }
        } catch (e: Exception) {
            Either.Left(value = DomainError.NetworkError(message = e.message ?: "Unknown network error"))
        }

    private fun mapUsage(response: ChatResponse): UsageRecord =
        UsageRecord(
            promptTokens = TokenCount(value = response.usage?.promptTokens ?: 0),
            completionTokens = TokenCount(value = response.usage?.completionTokens ?: 0),
            cachedTokens = TokenCount(value = response.usage?.promptTokensCacheRead ?: 0),
            cacheWriteTokens = TokenCount(value = response.usage?.promptTokensCacheWrite ?: 0),
            reasoningTokens = TokenCount(value = response.usage?.reasoningTokens ?: 0),
            totalCost = Cost(value = BigDecimal.valueOf(response.usage?.cost ?: 0.0)),
            upstreamCost = Cost(value = BigDecimal.valueOf(response.usage?.costDetails?.upstreamCost ?: 0.0)),
            upstreamPromptCost = Cost(value = BigDecimal.valueOf(response.usage?.costDetails?.upstreamPromptCost ?: 0.0)),
            upstreamCompletionsCost = Cost(value = BigDecimal.valueOf(response.usage?.costDetails?.upstreamCompletionsCost ?: 0.0)),
        )
}
```

Note: Check exact field names on `ChatResponse.Usage` and `ChatResponse.CostDetails` — they may differ slightly. Read the current `ChatResponse.kt` and adjust the mapping accordingly.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :modules:data:open-router-service:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add modules/data/open-router-service/
git commit -m "feat: create OpenRouterAdapter implementing LlmPort (ACL)"
```

---

### Task 7: Update AiChatService to use LlmPort

**Files:**
- Modify: `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiChatService.kt`
- Modify: `modules/domain/ai-agent/build.gradle.kts`

- [ ] **Step 1: Update AiChatService constructor — replace OpenRouterService with LlmPort**

Change constructor to accept `LlmPort` instead of `OpenRouterService` + `model`:

```kotlin
class AiChatService(
    private val llmPort: LlmPort,
    private val contextManager: ContextManager,
    private val repository: AgentSessionRepository,
) : ChatService {
```

Remove import of `OpenRouterService` and `ChatScope`. Add import of `LlmPort`.

- [ ] **Step 2: Rewrite send() to use LlmPort**

Replace the OpenRouterService call and response mapping with:

```kotlin
val llmResult = llmPort.complete(
    messages = preparedContext.messages,
    responseFormat = ResponseFormat.Text,
).bind()

val turn = Turn(
    id = TurnId.generate(),
    sessionId = sessionId,
    userMessage = message,
    assistantMessage = llmResult.content,
    usage = llmResult.usage,
    createdAt = CreatedAt(value = Clock.System.now()),
)
```

Remove the old `mapUsage()` private function and all `ChatResponse` handling — that is now in `OpenRouterAdapter`.

- [ ] **Step 3: Remove open-router-service dependency from ai-agent build**

In `modules/domain/ai-agent/build.gradle.kts`, remove:
```kotlin
implementation(project(":modules:data:open-router-service"))
```

The module now depends only on `:modules:core` (which has `LlmPort`).

Also remove test dependencies on ktor-client-mock/content-negotiation/serialization if they were only used for OpenRouterService mocking.

- [ ] **Step 4: Update existing tests**

In `modules/domain/ai-agent/src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt`, update to use a fake `LlmPort` instead of Ktor MockEngine. Create a simple fake:

```kotlin
class FakeLlmPort(
    private val response: Either<DomainError, LlmResponse>,
) : LlmPort {
    override suspend fun complete(
        messages: List<ContextMessage>,
        responseFormat: ResponseFormat,
    ): Either<DomainError, LlmResponse> = response
}
```

Update test setup to use `FakeLlmPort` and pass it to `AiChatService`.

- [ ] **Step 5: Run tests**

Run: `./gradlew :modules:domain:ai-agent:test`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add modules/domain/ai-agent/
git commit -m "refactor: AiChatService uses LlmPort instead of OpenRouterService"
```

---

### Task 8: Update context-manager to use LlmPort

**Files:**
- Modify: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/LlmContextCompressor.kt`
- Modify: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/LlmFactExtractor.kt`
- Modify: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/ContextCompressor.kt`
- Modify: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/FactExtractor.kt`
- Modify: `modules/domain/context-manager/build.gradle.kts`

- [ ] **Step 1: Update LlmContextCompressor to use LlmPort**

Replace `OpenRouterService` + `model` with `LlmPort` in constructor. Replace the direct API call with:

```kotlin
val result = llmPort.complete(
    messages = listOf(
        ContextMessage(role = MessageRole.System, content = MessageContent(value = systemPrompt)),
        ContextMessage(role = MessageRole.User, content = MessageContent(value = userPrompt)),
    ),
    responseFormat = ResponseFormat.Text,
)
return when (result) {
    is Either.Right -> SummaryContent(value = result.value.content.value)
    is Either.Left -> SummaryContent(value = "Summary unavailable")
}
```

- [ ] **Step 2: Update LlmFactExtractor to use LlmPort**

Replace `OpenRouterService` + `model` with `LlmPort` in constructor. Replace the direct API call with:

```kotlin
val result = llmPort.complete(
    messages = listOf(
        ContextMessage(role = MessageRole.System, content = MessageContent(value = systemPrompt)),
        ContextMessage(role = MessageRole.User, content = MessageContent(value = userPrompt)),
    ),
    responseFormat = ResponseFormat.Json,
)
```

- [ ] **Step 3: Remove open-router-service dependency from context-manager build**

In `modules/domain/context-manager/build.gradle.kts`, remove:
```kotlin
implementation(project(":modules:data:open-router-service"))
```

Remove ktor serialization dependency from main dependencies (keep in test if needed for JSON parsing — check if `kotlinx.serialization.json.Json` is available from another dep).

Note: LlmFactExtractor uses `kotlinx.serialization.json.Json` for parsing the LLM response. This needs `ktor-serialization-kotlinx-json` or direct `kotlinx-serialization-json`. Check and add the appropriate dependency if needed.

- [ ] **Step 4: Update tests**

Update `LlmContextCompressorTest.kt` and `LlmFactExtractorTest.kt` to use `FakeLlmPort` instead of Ktor MockEngine. Copy the `FakeLlmPort` fake or create a shared test utility.

- [ ] **Step 5: Run tests**

Run: `./gradlew :modules:domain:context-manager:test`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add modules/domain/context-manager/
git commit -m "refactor: context-manager uses LlmPort instead of OpenRouterService"
```

---

### Task 9: Update DI wiring for LlmPort

**Files:**
- Modify: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt`

- [ ] **Step 1: Add OpenRouterAdapter as LlmPort in DI**

In `AppModule.kt`, replace the model parameter passing to services with a single `LlmPort` binding:

```kotlin
single<LlmPort> {
    OpenRouterAdapter(
        openRouterService = get(),
        model = "google/gemini-2.0-flash-001",
    )
}
```

Update `AiChatService` binding to use `LlmPort`:
```kotlin
single<ChatService> {
    AiChatService(
        llmPort = get(),
        contextManager = get(),
        repository = get(),
    )
}
```

Update `LlmContextCompressor` and `LlmFactExtractor` bindings similarly.

- [ ] **Step 2: Verify full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt
git commit -m "refactor: wire LlmPort in DI, remove direct OpenRouterService from services"
```

---

## Phase 3: Domain Events Infrastructure

### Task 10: Create DomainEventPublisher and DomainEventHandler interfaces

**Files:**
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/event/DomainEventPublisher.kt`
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/event/DomainEventHandler.kt`

- [ ] **Step 1: Create DomainEventHandler**

Create `modules/core/src/main/kotlin/com/ai/challenge/core/event/DomainEventHandler.kt`:

```kotlin
package com.ai.challenge.core.event

/**
 * Domain Event Handler — processes a specific type of [DomainEvent].
 *
 * Each handler processes one event type. Registered in
 * [DomainEventPublisher] at application startup.
 *
 * Implementations must be idempotent — an event may be
 * delivered more than once in edge cases.
 */
interface DomainEventHandler<T : DomainEvent> {
    suspend fun handle(event: T)
}
```

- [ ] **Step 2: Create DomainEventPublisher**

Create `modules/core/src/main/kotlin/com/ai/challenge/core/event/DomainEventPublisher.kt`:

```kotlin
package com.ai.challenge.core.event

/**
 * Domain Event Publisher — dispatches events to registered handlers.
 *
 * In-process implementation: handlers are called synchronously
 * in the same coroutine. Guarantees that side effects (e.g.,
 * cleanup on [SessionDeleted]) complete before the publishing
 * operation returns.
 *
 * Interface defined in core; implemented in application layer
 * ([InProcessDomainEventPublisher]).
 */
interface DomainEventPublisher {
    suspend fun publish(event: DomainEvent)
}
```

- [ ] **Step 3: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/event/
git commit -m "feat: create DomainEventPublisher and DomainEventHandler interfaces"
```

---

### Task 11: Create InProcessDomainEventPublisher

**Files:**
- Create: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/event/InProcessDomainEventPublisher.kt`
- Create: `modules/presentation/app/src/test/kotlin/com/ai/challenge/app/event/InProcessDomainEventPublisherTest.kt`

- [ ] **Step 1: Write test for InProcessDomainEventPublisher**

Create `modules/presentation/app/src/test/kotlin/com/ai/challenge/app/event/InProcessDomainEventPublisherTest.kt`:

```kotlin
package com.ai.challenge.app.event

import com.ai.challenge.core.event.DomainEvent
import com.ai.challenge.core.event.DomainEventHandler
import com.ai.challenge.core.event.SessionDeleted
import com.ai.challenge.core.session.AgentSessionId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InProcessDomainEventPublisherTest {

    @Test
    fun `publish dispatches to registered handler`() = runTest {
        val handled = mutableListOf<DomainEvent>()
        val handler = object : DomainEventHandler<SessionDeleted> {
            override suspend fun handle(event: SessionDeleted) {
                handled.add(element = event)
            }
        }

        val publisher = InProcessDomainEventPublisher(
            handlers = mapOf(SessionDeleted::class to listOf(handler)),
        )

        val event = SessionDeleted(sessionId = AgentSessionId.generate())
        publisher.publish(event = event)

        assertEquals(expected = 1, actual = handled.size)
        assertEquals(expected = event, actual = handled.first())
    }

    @Test
    fun `publish with no handlers does not throw`() = runTest {
        val publisher = InProcessDomainEventPublisher(handlers = emptyMap())
        val event = SessionDeleted(sessionId = AgentSessionId.generate())
        publisher.publish(event = event)
        // No exception = success
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :modules:presentation:app:test --tests "com.ai.challenge.app.event.InProcessDomainEventPublisherTest"`
Expected: FAIL — class does not exist

Note: The app module may not have test configuration. If so, add to `modules/presentation/app/build.gradle.kts`:
```kotlin
testImplementation(kotlin("test"))
testImplementation(libs.kotlinx.coroutines.test)
```
And:
```kotlin
tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Implement InProcessDomainEventPublisher**

Create `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/event/InProcessDomainEventPublisher.kt`:

```kotlin
package com.ai.challenge.app.event

import com.ai.challenge.core.event.DomainEvent
import com.ai.challenge.core.event.DomainEventHandler
import com.ai.challenge.core.event.DomainEventPublisher
import kotlin.reflect.KClass

/**
 * In-process synchronous domain event dispatcher.
 *
 * Handlers are called sequentially in the same coroutine.
 * Guarantees that all side effects complete before [publish] returns.
 *
 * Registered handlers are organized by event type ([KClass]).
 * Each event type can have multiple handlers.
 */
class InProcessDomainEventPublisher(
    private val handlers: Map<KClass<out DomainEvent>, List<DomainEventHandler<*>>>,
) : DomainEventPublisher {

    @Suppress("UNCHECKED_CAST")
    override suspend fun publish(event: DomainEvent) {
        val eventHandlers = handlers[event::class] ?: return
        for (handler in eventHandlers) {
            (handler as DomainEventHandler<DomainEvent>).handle(event = event)
        }
    }
}
```

- [ ] **Step 4: Run test**

Run: `./gradlew :modules:presentation:app:test --tests "com.ai.challenge.app.event.InProcessDomainEventPublisherTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/presentation/app/
git commit -m "feat: create InProcessDomainEventPublisher"
```

---

### Task 12: Create SessionDeletedCleanupHandler

**Files:**
- Create: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/SessionDeletedCleanupHandler.kt`
- Create: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/SessionDeletedCleanupHandlerTest.kt`

- [ ] **Step 1: Write test**

Create `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/SessionDeletedCleanupHandlerTest.kt`:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.event.SessionDeleted
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SessionDeletedCleanupHandlerTest {

    @Test
    fun `handle deletes facts and summaries for session`() = runTest {
        val sessionId = AgentSessionId.generate()
        val factRepository = FakeFactRepository()
        val summaryRepository = FakeSummaryRepository()

        // Seed data
        factRepository.save(
            sessionId = sessionId,
            facts = listOf(
                Fact(
                    sessionId = sessionId,
                    category = FactCategory.Goal,
                    key = FactKey(value = "test"),
                    value = FactValue(value = "value"),
                )
            ),
        )

        val handler = SessionDeletedCleanupHandler(
            factRepository = factRepository,
            summaryRepository = summaryRepository,
        )

        handler.handle(event = SessionDeleted(sessionId = sessionId))

        assertTrue(actual = factRepository.getBySession(sessionId = sessionId).isEmpty())
        assertTrue(actual = summaryRepository.getBySession(sessionId = sessionId).isEmpty())
    }
}
```

Note: Use the existing `TestFakes.kt` in the test directory, or create `FakeFactRepository` / `FakeSummaryRepository` as simple in-memory implementations.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :modules:domain:context-manager:test --tests "com.ai.challenge.context.SessionDeletedCleanupHandlerTest"`
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement SessionDeletedCleanupHandler**

Create `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/SessionDeletedCleanupHandler.kt`:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.event.DomainEventHandler
import com.ai.challenge.core.event.SessionDeleted
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.summary.SummaryRepository

/**
 * Event Handler — cleans up Context Management data
 * when a session is deleted in Conversation Context.
 *
 * Resolves orphaning: without this handler, facts and summaries
 * persist in the database after session deletion because they
 * live in a separate bounded context (separate DB).
 *
 * Listens to: [SessionDeleted]
 * Actions: deletes all [Fact]s and [Summary]s for the session.
 *
 * Belongs to Context Management bounded context.
 */
class SessionDeletedCleanupHandler(
    private val factRepository: FactRepository,
    private val summaryRepository: SummaryRepository,
) : DomainEventHandler<SessionDeleted> {

    override suspend fun handle(event: SessionDeleted) {
        factRepository.deleteBySession(sessionId = event.sessionId)
        summaryRepository.deleteBySession(sessionId = event.sessionId)
    }
}
```

- [ ] **Step 4: Run test**

Run: `./gradlew :modules:domain:context-manager:test --tests "com.ai.challenge.context.SessionDeletedCleanupHandlerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/domain/context-manager/src/
git commit -m "feat: create SessionDeletedCleanupHandler for inter-context cleanup"
```

---

### Task 13: Wire domain events in DI

**Files:**
- Modify: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt`

- [ ] **Step 1: Register event handlers and publisher in Koin**

Add to `AppModule.kt`:

```kotlin
// Domain Events
single {
    SessionDeletedCleanupHandler(
        factRepository = get(),
        summaryRepository = get(),
    )
}

single<DomainEventPublisher> {
    InProcessDomainEventPublisher(
        handlers = mapOf(
            SessionDeleted::class to listOf(get<SessionDeletedCleanupHandler>()),
        ),
    )
}
```

Add necessary imports for `SessionDeletedCleanupHandler`, `InProcessDomainEventPublisher`, `DomainEventPublisher`, `SessionDeleted`.

- [ ] **Step 2: Verify full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt
git commit -m "feat: wire domain event publisher and handlers in DI"
```

---

## Phase 4: Context Strategy Decomposition

### Task 14: Create ContextStrategyConfig and ContextStrategy interface

**Files:**
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextStrategyConfig.kt`
- Create: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/ContextStrategy.kt`

- [ ] **Step 1: Create ContextStrategyConfig**

Create `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextStrategyConfig.kt`:

```kotlin
package com.ai.challenge.core.context

/**
 * Value Object — configuration for a context management strategy.
 *
 * Sealed hierarchy: each variant contains only parameters
 * needed by the corresponding [ContextStrategy] implementation.
 * New strategy = new subclass, without modifying existing ones.
 *
 * Linked 1:1 with [ContextManagementType]: type selects the strategy,
 * config parameterizes its behavior.
 */
sealed interface ContextStrategyConfig {

    /**
     * Passthrough — no processing. No parameters.
     */
    data object None : ContextStrategyConfig

    /**
     * Summarization on threshold.
     *
     * @param maxTurnsBeforeCompression — turns count triggering compression
     * @param retainLastTurns — recent turns kept uncompressed
     * @param compressionInterval — minimum turns between compressions
     */
    data class SummarizeOnThreshold(
        val maxTurnsBeforeCompression: Int,
        val retainLastTurns: Int,
        val compressionInterval: Int,
    ) : ContextStrategyConfig

    /**
     * Sliding window — keeps only last N turns.
     *
     * @param windowSize — number of turns in window
     */
    data class SlidingWindow(
        val windowSize: Int,
    ) : ContextStrategyConfig

    /**
     * Sticky facts — LLM-extracted facts between messages.
     *
     * @param retainLastTurns — recent turns passed alongside facts
     */
    data class StickyFacts(
        val retainLastTurns: Int,
    ) : ContextStrategyConfig

    /**
     * Branching — passthrough for current branch. No parameters.
     */
    data object Branching : ContextStrategyConfig
}
```

- [ ] **Step 2: Create ContextStrategy interface**

Create `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/ContextStrategy.kt`:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextStrategyConfig
import com.ai.challenge.core.context.PreparedContext
import com.ai.challenge.core.session.AgentSessionId

/**
 * Domain Service — strategy for preparing LLM context.
 *
 * Each implementation encapsulates one context management algorithm.
 * Strategy is selected by session's [ContextManagementType] and
 * parameterized by [ContextStrategyConfig].
 *
 * Stateless — no state between calls.
 */
interface ContextStrategy {
    suspend fun prepare(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        config: ContextStrategyConfig,
    ): PreparedContext
}
```

- [ ] **Step 3: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextStrategyConfig.kt \
  modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/ContextStrategy.kt
git commit -m "feat: create ContextStrategyConfig sealed hierarchy and ContextStrategy interface"
```

---

### Task 15: Extract PassthroughStrategy and SlidingWindowStrategy

**Files:**
- Create: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/PassthroughStrategy.kt`
- Create: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/SlidingWindowStrategy.kt`
- Create: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/PassthroughStrategyTest.kt`
- Create: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/SlidingWindowStrategyTest.kt`

- [ ] **Step 1: Write test for PassthroughStrategy**

Create `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/PassthroughStrategyTest.kt`. Test that it returns all turns as context messages without compression. Use fake `TurnReader` (see TestFakes.kt for existing fakes or create one).

Key assertions:
- `compressed == false`
- `originalTurnCount == retainedTurnCount`
- Messages include system prompt + all turn pairs + new user message

- [ ] **Step 2: Implement PassthroughStrategy**

Create `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/PassthroughStrategy.kt`:

Extract the `None` branch logic from `DefaultContextManager.prepareContext()`. The strategy reads turns via a `TurnReader` port (or `AgentSessionRepository` — use whatever the current code uses) and returns full history.

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.*
import com.ai.challenge.core.session.AgentSessionId

/**
 * Domain Service — passthrough context strategy.
 *
 * Returns full conversation history without any compression
 * or summarization. Used when [ContextManagementType.None] is selected.
 *
 * Simplest strategy — serves as baseline for testing.
 */
class PassthroughStrategy(
    private val repository: AgentSessionRepository,
) : ContextStrategy {

    override suspend fun prepare(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        config: ContextStrategyConfig,
    ): PreparedContext {
        val turns = repository.getTurnsByBranch(branchId = branchId)
        val messages = buildList {
            for (turn in turns) {
                add(element = ContextMessage(role = MessageRole.User, content = turn.userMessage))
                add(element = ContextMessage(role = MessageRole.Assistant, content = turn.assistantMessage))
            }
            add(element = ContextMessage(role = MessageRole.User, content = newMessage))
        }
        return PreparedContext(
            messages = messages,
            compressed = false,
            originalTurnCount = turns.size,
            retainedTurnCount = turns.size,
            summaryCount = 0,
        )
    }
}
```

Note: Check exact `PreparedContext` constructor parameters — adjust to match current code.

- [ ] **Step 3: Run PassthroughStrategy test**

Run: `./gradlew :modules:domain:context-manager:test --tests "com.ai.challenge.context.PassthroughStrategyTest"`
Expected: PASS

- [ ] **Step 4: Write test for SlidingWindowStrategy**

Test that only last N turns are included. Key assertions:
- `retainedTurnCount == min(windowSize, totalTurns)`
- Only the last `windowSize` turns appear in messages
- `compressed == true` when turns were truncated

- [ ] **Step 5: Implement SlidingWindowStrategy**

Extract the `SlidingWindow` branch logic from `DefaultContextManager`. The strategy takes `ContextStrategyConfig.SlidingWindow` and keeps last `windowSize` turns.

- [ ] **Step 6: Run SlidingWindowStrategy test**

Run: `./gradlew :modules:domain:context-manager:test --tests "com.ai.challenge.context.SlidingWindowStrategyTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add modules/domain/context-manager/src/
git commit -m "feat: extract PassthroughStrategy and SlidingWindowStrategy"
```

---

### Task 16: Extract SummarizeOnThresholdStrategy

**Files:**
- Create: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/SummarizeOnThresholdStrategy.kt`
- Create: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/SummarizeOnThresholdStrategyTest.kt`

- [ ] **Step 1: Write tests**

Test cases:
1. When turn count < maxTurnsBeforeCompression → no compression, returns full history
2. When turn count >= maxTurnsBeforeCompression and compressionInterval reached → compresses, retains last N, includes summary in system message
3. When compression already happened recently (< compressionInterval) → no additional compression

- [ ] **Step 2: Implement SummarizeOnThresholdStrategy**

Extract the `SummarizeOnThreshold` branch logic from `DefaultContextManager` (lines ~79-107). The strategy:
1. Reads turns via repository
2. Reads existing summaries via `SummaryRepository`
3. Checks if compression is needed (threshold + interval)
4. If yes: calls `ContextCompressor`, saves summary, returns compressed context
5. If no: returns full context with existing summaries

```kotlin
class SummarizeOnThresholdStrategy(
    private val repository: AgentSessionRepository,
    private val summaryRepository: SummaryRepository,
    private val compressor: ContextCompressor,
) : ContextStrategy
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :modules:domain:context-manager:test --tests "com.ai.challenge.context.SummarizeOnThresholdStrategyTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add modules/domain/context-manager/src/
git commit -m "feat: extract SummarizeOnThresholdStrategy"
```

---

### Task 17: Extract StickyFactsStrategy

**Files:**
- Create: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/StickyFactsStrategy.kt`
- Create: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/StickyFactsStrategyTest.kt`

- [ ] **Step 1: Write tests**

Test cases:
1. Extracts facts via LLM, saves them, builds context with facts in system prompt + recent turns
2. When fact extraction fails → falls back to existing facts
3. When no existing facts → context contains only recent turns

- [ ] **Step 2: Implement StickyFactsStrategy**

Extract the `StickyFacts` branch logic from `DefaultContextManager` (lines ~124-160). The strategy:
1. Reads turns, reads current facts
2. Calls `FactExtractor` for updated facts
3. Saves updated facts via `FactRepository`
4. Builds system prompt with formatted facts
5. Returns context with facts + last N turns

```kotlin
class StickyFactsStrategy(
    private val repository: AgentSessionRepository,
    private val factRepository: FactRepository,
    private val factExtractor: FactExtractor,
) : ContextStrategy
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :modules:domain:context-manager:test --tests "com.ai.challenge.context.StickyFactsStrategyTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add modules/domain/context-manager/src/
git commit -m "feat: extract StickyFactsStrategy"
```

---

### Task 18: Create ContextPreparationService, replace DefaultContextManager

**Files:**
- Create: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/ContextPreparationService.kt`
- Create: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/ContextPreparationServiceTest.kt`
- Delete: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt`
- Delete: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt`
- Modify: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt`

- [ ] **Step 1: Write test for ContextPreparationService**

Test that it routes to the correct strategy based on session's ContextManagementType. Use fake strategies.

- [ ] **Step 2: Implement ContextPreparationService**

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.*
import com.ai.challenge.core.session.AgentSessionId

/**
 * Domain Service — orchestrator for context preparation.
 * Implements [ContextManager].
 *
 * Responsibilities:
 * 1. Loads session to determine [ContextManagementType]
 * 2. Selects corresponding [ContextStrategy]
 * 3. Resolves [ContextStrategyConfig] for the strategy
 * 4. Delegates context preparation
 *
 * Contains no strategy logic — only routing.
 * Open-Closed: new strategy = new class + registration,
 * no modification of this class.
 */
class ContextPreparationService(
    private val strategies: Map<ContextManagementType, ContextStrategy>,
    private val configs: Map<ContextManagementType, ContextStrategyConfig>,
    private val repository: AgentSessionRepository,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
    ): PreparedContext {
        val session = repository.get(id = sessionId)
            ?: error("Session not found: $sessionId")
        val type = session.contextManagementType
        val strategy = strategies[type]
            ?: error("No strategy registered for: $type")
        val config = configs[type]
            ?: error("No config registered for: $type")
        return strategy.prepare(
            sessionId = sessionId,
            branchId = branchId,
            newMessage = newMessage,
            config = config,
        )
    }
}
```

- [ ] **Step 3: Run test**

Run: `./gradlew :modules:domain:context-manager:test --tests "com.ai.challenge.context.ContextPreparationServiceTest"`
Expected: PASS

- [ ] **Step 4: Delete DefaultContextManager and its test**

Remove:
- `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt`
- `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt`

- [ ] **Step 5: Update DI — replace DefaultContextManager with ContextPreparationService**

In `AppModule.kt`, replace the `DefaultContextManager` binding with:

```kotlin
// Context strategies
single { PassthroughStrategy(repository = get()) }
single { SlidingWindowStrategy(repository = get()) }
single {
    SummarizeOnThresholdStrategy(
        repository = get(),
        summaryRepository = get(),
        compressor = get(),
    )
}
single {
    StickyFactsStrategy(
        repository = get(),
        factRepository = get(),
        factExtractor = get(),
    )
}
single { BranchingContextManager(repository = get()) }

// Strategy configs
val strategyConfigs: Map<ContextManagementType, ContextStrategyConfig> = mapOf(
    ContextManagementType.None to ContextStrategyConfig.None,
    ContextManagementType.SummarizeOnThreshold to ContextStrategyConfig.SummarizeOnThreshold(
        maxTurnsBeforeCompression = 15,
        retainLastTurns = 5,
        compressionInterval = 10,
    ),
    ContextManagementType.SlidingWindow to ContextStrategyConfig.SlidingWindow(windowSize = 10),
    ContextManagementType.StickyFacts to ContextStrategyConfig.StickyFacts(retainLastTurns = 5),
    ContextManagementType.Branching to ContextStrategyConfig.Branching,
)

single<ContextManager> {
    ContextPreparationService(
        strategies = mapOf(
            ContextManagementType.None to get<PassthroughStrategy>(),
            ContextManagementType.SummarizeOnThreshold to get<SummarizeOnThresholdStrategy>(),
            ContextManagementType.SlidingWindow to get<SlidingWindowStrategy>(),
            ContextManagementType.StickyFacts to get<StickyFactsStrategy>(),
            ContextManagementType.Branching to get<BranchingContextManager>(),
        ),
        configs = strategyConfigs,
        repository = get(),
    )
}
```

Note: `BranchingContextManager` already exists and implements `ContextManager`. It needs to implement `ContextStrategy` instead, or be wrapped. Check the current interface and adjust.

- [ ] **Step 6: Run all tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: replace DefaultContextManager with ContextPreparationService + strategies"
```

---

## Phase 5: Application Services

### Task 19: Create SendMessageUseCase

**Files:**
- Create: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/usecase/SendMessageUseCase.kt`
- Create: `modules/presentation/app/src/test/kotlin/com/ai/challenge/app/usecase/SendMessageUseCaseTest.kt`

- [ ] **Step 1: Write test**

Test that SendMessageUseCase:
1. Calls ChatService.send()
2. Publishes TurnRecorded event
3. Updates title from first message if title is empty

Use fakes for ChatService, SessionService, DomainEventPublisher.

- [ ] **Step 2: Implement SendMessageUseCase**

```kotlin
package com.ai.challenge.app.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.ChatService
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.event.DomainEventPublisher
import com.ai.challenge.core.event.TurnRecorded
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn

/**
 * Application Service — send message use case.
 *
 * Orchestrates:
 * 1. Delegates to [ChatService] for context preparation, LLM call, and Turn save
 * 2. Publishes [TurnRecorded] event for Context Management context
 * 3. Auto-generates session title from first message (if empty)
 *
 * Presentation layer calls this use case instead of [ChatService] directly.
 */
class SendMessageUseCase(
    private val chatService: ChatService,
    private val sessionService: SessionService,
    private val eventPublisher: DomainEventPublisher,
) {

    suspend fun execute(
        sessionId: AgentSessionId,
        branchId: BranchId,
        message: MessageContent,
    ): Either<DomainError, Turn> = either {
        val turn = chatService.send(
            sessionId = sessionId,
            branchId = branchId,
            message = message,
        ).bind()

        eventPublisher.publish(
            event = TurnRecorded(
                sessionId = sessionId,
                turn = turn,
                branchId = branchId,
            )
        )

        val session = sessionService.get(id = sessionId).bind()
        if (session.title.value.isEmpty()) {
            sessionService.updateTitle(
                id = sessionId,
                title = SessionTitle(value = message.value.take(n = 50)),
            )
        }

        turn
    }
}
```

- [ ] **Step 3: Run test**

Run: `./gradlew :modules:presentation:app:test --tests "com.ai.challenge.app.usecase.SendMessageUseCaseTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add modules/presentation/app/src/
git commit -m "feat: create SendMessageUseCase application service"
```

---

### Task 20: Create CreateSessionUseCase and DeleteSessionUseCase

**Files:**
- Create: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/usecase/CreateSessionUseCase.kt`
- Create: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/usecase/DeleteSessionUseCase.kt`
- Create: `modules/presentation/app/src/test/kotlin/com/ai/challenge/app/usecase/CreateSessionUseCaseTest.kt`
- Create: `modules/presentation/app/src/test/kotlin/com/ai/challenge/app/usecase/DeleteSessionUseCaseTest.kt`

- [ ] **Step 1: Write test for CreateSessionUseCase**

Test that it creates session via SessionService, publishes SessionCreated event.

- [ ] **Step 2: Implement CreateSessionUseCase**

```kotlin
package com.ai.challenge.app.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.event.DomainEventPublisher
import com.ai.challenge.core.event.SessionCreated
import com.ai.challenge.core.session.AgentSession

/**
 * Application Service — create session use case.
 *
 * Orchestrates:
 * 1. Creates [AgentSession] via [SessionService] (includes main Branch creation)
 * 2. Publishes [SessionCreated] event
 *
 * Guarantees: session always created with main branch (enforced by [SessionService]).
 */
class CreateSessionUseCase(
    private val sessionService: SessionService,
    private val eventPublisher: DomainEventPublisher,
) {

    suspend fun execute(
        title: SessionTitle,
    ): Either<DomainError, AgentSession> = either {
        val session = sessionService.create(title = title).bind()

        eventPublisher.publish(
            event = SessionCreated(sessionId = session.id)
        )

        session
    }
}
```

- [ ] **Step 3: Run test**

Expected: PASS

- [ ] **Step 4: Write test for DeleteSessionUseCase**

Test that it deletes session, publishes SessionDeleted event (which triggers cleanup handler).

- [ ] **Step 5: Implement DeleteSessionUseCase**

```kotlin
package com.ai.challenge.app.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.event.DomainEventPublisher
import com.ai.challenge.core.event.SessionDeleted
import com.ai.challenge.core.session.AgentSessionId

/**
 * Application Service — delete session use case.
 *
 * Orchestrates:
 * 1. Deletes aggregate via [SessionService]
 * 2. Publishes [SessionDeleted] event
 *    → Context Management cleans up Facts and Summaries
 *
 * Does NOT contain "always one session" policy —
 * that is [ApplicationInitService] responsibility.
 */
class DeleteSessionUseCase(
    private val sessionService: SessionService,
    private val eventPublisher: DomainEventPublisher,
) {

    suspend fun execute(
        sessionId: AgentSessionId,
    ): Either<DomainError, Unit> = either {
        sessionService.delete(id = sessionId).bind()

        eventPublisher.publish(
            event = SessionDeleted(sessionId = sessionId)
        )
    }
}
```

- [ ] **Step 6: Run test**

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add modules/presentation/app/src/
git commit -m "feat: create CreateSessionUseCase and DeleteSessionUseCase"
```

---

### Task 21: Create ApplicationInitService and SwitchBranchUseCase

**Files:**
- Create: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/usecase/ApplicationInitService.kt`
- Create: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/usecase/SwitchBranchUseCase.kt`

- [ ] **Step 1: Implement ApplicationInitService**

```kotlin
package com.ai.challenge.app.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSession

/**
 * Application Service — application initialization.
 *
 * Contains application-level UX policies that are NOT domain rules:
 * - "At least one session always exists"
 * - Default session creation on first launch
 *
 * Called once at application startup and after last session deletion.
 *
 * This is NOT a domain rule (domain doesn't care about 0 or 100 sessions).
 * It's a UX policy to ensure the user always has an active session.
 */
class ApplicationInitService(
    private val createSessionUseCase: CreateSessionUseCase,
    private val sessionService: SessionService,
) {

    suspend fun ensureAtLeastOneSession(): Either<DomainError, AgentSession?> = either {
        val sessions = sessionService.list().bind()
        if (sessions.isEmpty()) {
            createSessionUseCase.execute(title = SessionTitle(value = "")).bind()
        } else {
            null
        }
    }
}
```

- [ ] **Step 2: Implement SwitchBranchUseCase**

```kotlin
package com.ai.challenge.app.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.BranchService
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.UsageService
import com.ai.challenge.core.usage.model.UsageRecord

/**
 * Application Service — switch active branch and load its data.
 *
 * Orchestrates data loading for UI on branch switch:
 * 1. Loads turns for the branch
 * 2. Loads usage for each turn
 * 3. Computes session total
 *
 * Eliminates duplicated loading logic that was previously
 * repeated in handleLoadSession and handleSwitchBranch in ChatStoreFactory.
 */
class SwitchBranchUseCase(
    private val branchService: BranchService,
    private val usageService: UsageService,
) {

    data class BranchData(
        val turns: List<Turn>,
        val turnUsage: Map<TurnId, UsageRecord>,
        val sessionTotal: UsageRecord,
    )

    suspend fun execute(
        branchId: BranchId,
    ): Either<DomainError, BranchData> = either {
        val turns = branchService.getTurns(branchId = branchId).bind()

        val turnUsage = mutableMapOf<TurnId, UsageRecord>()
        for (turn in turns) {
            val usage = usageService.getByTurn(turnId = turn.id).bind()
            turnUsage[turn.id] = usage
        }

        val sessionTotal = turns.fold(initial = UsageRecord.ZERO) { acc, turn ->
            acc + turnUsage.getValue(key = turn.id)
        }

        BranchData(
            turns = turns,
            turnUsage = turnUsage,
            sessionTotal = sessionTotal,
        )
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add modules/presentation/app/src/main/kotlin/com/ai/challenge/app/usecase/
git commit -m "feat: create ApplicationInitService and SwitchBranchUseCase"
```

---

### Task 22: Wire Application Services in DI and update Stores

**Files:**
- Modify: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt`
- Modify: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/Main.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/sessionlist/store/SessionListStoreFactory.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootComponent.kt`
- Modify: `modules/presentation/compose-ui/build.gradle.kts`

- [ ] **Step 1: Register Application Services in Koin**

Add to `AppModule.kt`:

```kotlin
// Application Services
single {
    SendMessageUseCase(
        chatService = get(),
        sessionService = get(),
        eventPublisher = get(),
    )
}
single {
    CreateSessionUseCase(
        sessionService = get(),
        eventPublisher = get(),
    )
}
single {
    DeleteSessionUseCase(
        sessionService = get(),
        eventPublisher = get(),
    )
}
single {
    ApplicationInitService(
        createSessionUseCase = get(),
        sessionService = get(),
    )
}
single {
    SwitchBranchUseCase(
        branchService = get(),
        usageService = get(),
    )
}
```

- [ ] **Step 2: Add app module dependency on compose-ui (if not already) for use case visibility**

The use cases live in the `app` module. The stores in `compose-ui` need access to them. Since `compose-ui` cannot depend on `app` (it's the other way around), the use case interfaces need to be either:
- Defined in `core` as interfaces, implemented in `app` — **preferred**
- Or injected via Koin without compile-time dependency

Since Koin is used, stores receive use cases through DI. Add the `app` module classes to compose-ui's DI scope. Actually, the stores get their dependencies injected via factory parameters — check the current pattern.

The simplest approach: define use case interfaces in `core`, implement in `app`. But this adds complexity. Alternative: keep use cases in `app` and pass them to stores via component constructors (current Decompose pattern).

Check the current ChatComponent/ChatStoreFactory pattern to determine how dependencies flow. Then wire use cases the same way.

- [ ] **Step 3: Update ChatStoreFactory — replace direct service calls with use cases**

Replace `chatService.send()` with `sendMessageUseCase.execute()`.
Remove title auto-generation logic (it's in SendMessageUseCase now).
Remove inline usage aggregation — use `switchBranchUseCase.execute()` for data loading.
Remove `emptyUsageRecord()` function (use `UsageRecord.ZERO`).

- [ ] **Step 4: Update RootComponent — use ApplicationInitService**

Replace the `runBlocking` initialization logic with `ApplicationInitService.ensureAtLeastOneSession()`.
Replace `createNewSession()` calls with `createSessionUseCase.execute()`.
Replace delete logic with `deleteSessionUseCase.execute()`.

- [ ] **Step 5: Run full build and test**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 6: Manual smoke test**

Run: `OPENROUTER_API_KEY=<key> ./gradlew :modules:presentation:app:run`
Test: create session, send message, check title auto-generates, create branch, switch branch, delete session.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: wire application services, thin out stores and components"
```

---

## Phase 6: Rename UsageService to UsageQueryService

### Task 23: Rename UsageService

**Files:**
- Rename: `modules/core/src/main/kotlin/com/ai/challenge/core/usage/UsageService.kt` → keep file, rename interface
- Rename: `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiUsageService.kt` → rename class
- Update all references

- [ ] **Step 1: Rename interface to UsageQueryService**

In `UsageService.kt`, rename interface from `UsageService` to `UsageQueryService`.

- [ ] **Step 2: Rename implementation to AiUsageQueryService**

In `AiUsageService.kt`, rename class from `AiUsageService` to `AiUsageQueryService`.

- [ ] **Step 3: Update all references**

Update imports and references in:
- `AppModule.kt` (DI binding)
- `ChatStoreFactory.kt` / `ChatComponent.kt` (if still used directly)
- `SwitchBranchUseCase.kt`
- Any test files

- [ ] **Step 4: Run full tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: rename UsageService to UsageQueryService for DDD clarity"
```

---

## Phase 7: Final Verification

### Task 24: Full integration verification

- [ ] **Step 1: Full build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL with zero warnings related to our changes

- [ ] **Step 2: Full test suite**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 3: Manual smoke test**

Run: `OPENROUTER_API_KEY=<key> ./gradlew :modules:presentation:app:run`

Test checklist:
- Create session → title empty
- Send message → title auto-generates from first message
- Send several messages → context flows correctly
- Switch context management type to SlidingWindow → verify works
- Switch to StickyFacts → verify fact extraction
- Switch to SummarizeOnThreshold → verify compression
- Create branch → switch between branches
- Delete non-main branch → verify
- Delete session → verify facts/summaries cleaned up (check DB files)
- Verify "always one session" policy works on last deletion

- [ ] **Step 4: Final commit with summary**

```bash
git add -A
git commit -m "refactor: complete strict DDD refactoring — bounded contexts, events, ACL, strategies"
```
