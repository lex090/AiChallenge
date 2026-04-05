# Context Compression Design

## Overview

Mechanism for managing AI agent conversation context through history compression. Instead of sending full conversation history with every request, older messages are replaced with LLM-generated summaries while recent messages are kept intact. This reduces token usage while preserving conversation quality.

## Requirements

- Store last N messages as-is, replace the rest with summaries
- Summaries generated via LLM (OpenRouter)
- Summaries stored persistently in DB and reused (cached)
- Compression happens synchronously before sending a request, only when needed
- Extensible: pluggable compression types and trigger strategies
- Minimal changes to existing code — no changes to presentation layer or existing repositories
- Token/cost tracking via existing TokenDetails/CostDetails infrastructure

## Core Abstractions (modules/core)

### MessageRole

```kotlin
enum class MessageRole {
    System,
    User,
    Assistant,
}
```

### ContextMessage

A single message in the prepared context. Not tied to Turn — can represent a summary or a regular message.

```kotlin
data class ContextMessage(
    val role: MessageRole,
    val content: String,
)
```

### CompressedContext

Result of context preparation. Contains the messages to send plus metadata about compression.

```kotlin
data class CompressedContext(
    val messages: List<ContextMessage>,
    val compressed: Boolean,
    val originalTurnCount: Int,
    val retainedTurnCount: Int,
    val summaryCount: Int,
)
```

### ContextManager

Pure transformation: takes conversation history + new message, returns prepared context. Does not send requests to LLM itself (except via ContextCompressor for summarization).

```kotlin
interface ContextManager {
    suspend fun prepareContext(
        sessionId: SessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext
}
```

### CompressionStrategy

Determines when to compress and where to split the history.

```kotlin
interface CompressionStrategy {
    fun shouldCompress(history: List<Turn>): Boolean
    fun partitionPoint(history: List<Turn>): Int
}
```

`partitionPoint` returns the index: everything before it goes to compression, everything from it onward is retained as-is.

### ContextCompressor

Performs the actual compression of turns into a summary text.

```kotlin
interface ContextCompressor {
    suspend fun compress(turns: List<Turn>): String
}
```

### Summary and SummaryRepository

Persistent storage for generated summaries.

```kotlin
@JvmInline
value class SummaryId(val value: String) {
    companion object {
        fun generate(): SummaryId = SummaryId(UUID.randomUUID().toString())
    }
}

data class Summary(
    val id: SummaryId,
    val text: String,
    val fromTurnIndex: Int,
    val toTurnIndex: Int,
    val createdAt: Instant,
)

interface SummaryRepository {
    suspend fun save(sessionId: SessionId, summary: Summary)
    suspend fun getBySession(sessionId: SessionId): List<Summary>
}
```

## Implementations

### TurnCountStrategy (modules/domain/context-manager)

Default strategy based on turn count.

```kotlin
class TurnCountStrategy(
    private val maxTurns: Int,
    private val retainLast: Int,
) : CompressionStrategy {

    override fun shouldCompress(history: List<Turn>): Boolean =
        history.size > maxTurns

    override fun partitionPoint(history: List<Turn>): Int =
        (history.size - retainLast).coerceAtLeast(0)
}
```

Default configuration: `maxTurns = 15`, `retainLast = 5`.

### LlmContextCompressor (modules/data/context-compressor-llm)

Summarizes turns via OpenRouter API call.

```kotlin
class LlmContextCompressor(
    private val service: OpenRouterService,
    private val model: String,
) : ContextCompressor {

    override suspend fun compress(turns: List<Turn>): String {
        return service.chatText(model) {
            system("Summarize the following conversation concisely, preserving key facts, decisions, and context needed for continuation.")
            for (turn in turns) {
                user(turn.userMessage)
                assistant(turn.agentResponse)
            }
            user("Provide a concise summary of the conversation above.")
        }
    }
}
```

### DefaultContextManager (modules/domain/context-manager)

Main implementation that orchestrates strategy, compressor, and summary caching.

```kotlin
class DefaultContextManager(
    private val strategy: CompressionStrategy,
    private val compressor: ContextCompressor,
    private val summaryRepository: SummaryRepository,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: SessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext {

        if (!strategy.shouldCompress(history)) {
            val messages = buildList {
                for (turn in history) {
                    add(ContextMessage(MessageRole.User, turn.userMessage))
                    add(ContextMessage(MessageRole.Assistant, turn.agentResponse))
                }
                add(ContextMessage(MessageRole.User, newMessage))
            }
            return CompressedContext(
                messages = messages,
                compressed = false,
                originalTurnCount = history.size,
                retainedTurnCount = history.size,
                summaryCount = 0,
            )
        }

        val splitAt = strategy.partitionPoint(history)
        val toCompress = history.subList(0, splitAt)
        val toRetain = history.subList(splitAt, history.size)

        val existingSummaries = summaryRepository.getBySession(sessionId)
        val cached = existingSummaries.find {
            it.fromTurnIndex == 0 && it.toTurnIndex == splitAt
        }

        val summaryText = if (cached != null) {
            cached.text
        } else {
            val text = compressor.compress(toCompress)
            summaryRepository.save(sessionId, Summary(
                id = SummaryId.generate(),
                text = text,
                fromTurnIndex = 0,
                toTurnIndex = splitAt,
                createdAt = Clock.System.now(),
            ))
            text
        }

        val messages = buildList {
            add(ContextMessage(MessageRole.System, "Previous conversation summary:\n$summaryText"))
            for (turn in toRetain) {
                add(ContextMessage(MessageRole.User, turn.userMessage))
                add(ContextMessage(MessageRole.Assistant, turn.agentResponse))
            }
            add(ContextMessage(MessageRole.User, newMessage))
        }

        return CompressedContext(
            messages = messages,
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = toRetain.size,
            summaryCount = 1,
        )
    }
}
```

Summary caching: if a summary for the same `[0, splitAt)` range already exists in the DB, it is reused. When history grows and `splitAt` shifts, a new summary is generated.

### ExposedSummaryRepository (modules/data/summary-repository-exposed)

Standard Exposed implementation with a `summaries` table: `id`, `session_id`, `text`, `from_turn_index`, `to_turn_index`, `created_at`.

## Integration with AiAgent

### Changes to AiAgent (modules/domain/ai-agent)

One new constructor parameter and ~3 lines changed in `send()`:

```kotlin
class AiAgent(
    private val service: OpenRouterService,
    private val model: String,
    private val sessionRepository: SessionRepository,
    private val turnRepository: TurnRepository,
    private val tokenRepository: TokenRepository,
    private val costRepository: CostRepository,
    private val contextManager: ContextManager,
) : Agent {

    override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> = either {
        val history = turnRepository.getBySession(sessionId)
        val context = contextManager.prepareContext(sessionId, history, message)

        val chatResponse = catch({
            service.chat(model = model) {
                for (msg in context.messages) {
                    message(msg.role.toApiRole(), msg.content)
                }
            }
        }) { /* error handling unchanged */ }

        // ... rest unchanged
    }
}

fun MessageRole.toApiRole(): String = when (this) {
    MessageRole.System -> "system"
    MessageRole.User -> "user"
    MessageRole.Assistant -> "assistant"
}
```

### DI Configuration (modules/presentation/app)

```kotlin
val appModule = module {
    single { OpenRouterService(apiKey = ...) }

    single<SessionRepository> { ExposedSessionRepository(createSessionDatabase()) }
    single<TurnRepository> { ExposedTurnRepository(createTurnDatabase()) }
    single<TokenRepository> { ExposedTokenRepository(createTokenDatabase()) }
    single<CostRepository> { ExposedCostRepository(createCostDatabase()) }
    single<SummaryRepository> { ExposedSummaryRepository(createSummaryDatabase()) }

    single<CompressionStrategy> { TurnCountStrategy(maxTurns = 15, retainLast = 5) }
    single<ContextCompressor> { LlmContextCompressor(service = get(), model = "google/gemini-2.0-flash-001") }
    single<ContextManager> { DefaultContextManager(strategy = get(), compressor = get(), summaryRepository = get()) }

    single<Agent> {
        AiAgent(
            service = get(),
            model = "google/gemini-2.0-flash-001",
            contextManager = get(),
            sessionRepository = get(),
            turnRepository = get(),
            tokenRepository = get(),
            costRepository = get(),
        )
    }
}
```

## New Module Structure

```
modules/
├── core/                              ← + MessageRole, ContextMessage, CompressedContext,
│                                        ContextManager, CompressionStrategy, ContextCompressor,
│                                        Summary, SummaryId, SummaryRepository
├── data/
│   ├── context-compressor-llm/        ← NEW: LlmContextCompressor
│   └── summary-repository-exposed/    ← NEW: ExposedSummaryRepository + SummariesTable
├── domain/
│   ├── ai-agent/                      ← +1 constructor param, ~3 lines changed in send()
│   └── context-manager/               ← NEW: DefaultContextManager, TurnCountStrategy
└── presentation/
    ├── app/                           ← DI config: new bindings
    └── compose-ui/                    ← NO CHANGES
```

## Impact Summary

| Module | Change |
|--------|--------|
| `core` | New interfaces and models (additive) |
| `ai-agent` | +1 constructor parameter, 3 lines in send() |
| `app` | New DI bindings |
| `context-compressor-llm` | NEW module |
| `context-manager` | NEW module |
| `summary-repository-exposed` | NEW module |
| `compose-ui` | No changes |
| `open-router-service` | No changes |
| All existing repositories | No changes |

## Testing

### DefaultContextManager (unit tests)

- History below threshold: returns all turns as-is, `compressed = false`
- History above threshold: calls compressor, returns summary + last N turns, `compressed = true`
- Summary caching: repeated call with same range reuses cached summary, compressor not called
- Range shift: growing history invalidates old summary, new one is created

Tested with fake implementations: `FakeContextCompressor`, `InMemorySummaryRepository`.

### TurnCountStrategy (unit tests)

- `shouldCompress` returns false/true based on threshold
- `partitionPoint` computes correct split index

### LlmContextCompressor (MockEngine test)

- Ktor MockEngine returns fake response
- Verify request contains correct system prompt and conversation messages

### AiAgent.send() (integration test)

- Fake ContextManager returns prepared context
- MockEngine verifies HTTP request contains exactly the messages from CompressedContext

## Extensibility

- **New compression types**: implement `ContextCompressor` (e.g., local algorithmic compression, different LLM prompt strategies)
- **New trigger strategies**: implement `CompressionStrategy` (e.g., token-count based, time-window based)
- **Incremental summaries**: future enhancement where new summaries build on previous ones instead of re-summarizing from scratch
