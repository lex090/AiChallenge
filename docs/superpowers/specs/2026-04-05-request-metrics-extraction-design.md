# Request Metrics Extraction Design

## Problem

Token usage is embedded directly into chat session entities (`Turn`, `UiMessage`, `ChatStore.State`), violating separation of concerns. Different consumers need different data: restoring dialog context for LLM doesn't need metrics, calculating session spend doesn't need message content. Additionally, cached tokens, reasoning tokens, and cost information from OpenRouter responses are ignored.

## Decision Record

- **Turn-level, not message-level**: OpenRouter returns usage once per request-response cycle. Metrics belong to the turn, not to individual messages.
- **Separate entity, not composition**: Turn and RequestMetrics have different read patterns (dialog restoration vs spend analytics), so they are stored separately and linked by `turnId`.
- **Separate manager**: `UsageManager` owns metrics storage independently of `SessionManager`, following Single Responsibility.
- **No `sessionId` denormalization in `request_metrics`**: join through `turns.session_id` is sufficient at current scale.

## Architecture

```
llm-service (API models)     session-storage (domain models)      compose-ui (presentation)

ChatResponse                  TokenDetails                         ChatStore.State
  usage                       CostDetails                            turnMetrics: Map<TurnId, RequestMetrics>
    prompt_tokens_details      RequestMetrics                         sessionMetrics: RequestMetrics
    completion_tokens_details    tokens: TokenDetails
    cost                         cost: CostDetails
  cost_details
                              Turn (no metrics)
                              UsageManager (separate from SessionManager)
```

## Data Model

### llm-service: API response models

Extend `ChatResponse.kt`:

```kotlin
@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    @SerialName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null,
    @SerialName("completion_tokens_details") val completionTokensDetails: CompletionTokensDetails? = null,
    val cost: Double? = null,
)

@Serializable
data class PromptTokensDetails(
    @SerialName("cached_tokens") val cachedTokens: Int = 0,
    @SerialName("cache_write_tokens") val cacheWriteTokens: Int = 0,
)

@Serializable
data class CompletionTokensDetails(
    @SerialName("reasoning_tokens") val reasoningTokens: Int = 0,
)

@Serializable
data class CostDetails(
    @SerialName("upstream_inference_cost") val upstreamCost: Double = 0.0,
    @SerialName("upstream_inference_prompt_cost") val upstreamPromptCost: Double = 0.0,
    @SerialName("upstream_inference_completions_cost") val upstreamCompletionsCost: Double = 0.0,
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
    val model: String? = null,
    val usage: Usage? = null,
    val error: ErrorBody? = null,
    val cost: Double? = null,
    @SerialName("cost_details") val costDetails: CostDetails? = null,
)
```

Note: `CostDetails` name collision between llm-service (serializable API model) and session-storage (domain value object). The llm-service version lives in `com.ai.challenge.llm.model`, the domain version in `com.ai.challenge.session` — different packages, no conflict.

### session-storage: domain value objects

```kotlin
// TokenDetails.kt
data class TokenDetails(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val cachedTokens: Int = 0,
    val cacheWriteTokens: Int = 0,
    val reasoningTokens: Int = 0,
) {
    val totalTokens: Int get() = promptTokens + completionTokens

    operator fun plus(other: TokenDetails) = TokenDetails(
        promptTokens = promptTokens + other.promptTokens,
        completionTokens = completionTokens + other.completionTokens,
        cachedTokens = cachedTokens + other.cachedTokens,
        cacheWriteTokens = cacheWriteTokens + other.cacheWriteTokens,
        reasoningTokens = reasoningTokens + other.reasoningTokens,
    )
}

// CostDetails.kt
data class CostDetails(
    val totalCost: Double = 0.0,
    val upstreamCost: Double = 0.0,
    val upstreamPromptCost: Double = 0.0,
    val upstreamCompletionsCost: Double = 0.0,
) {
    operator fun plus(other: CostDetails) = CostDetails(
        totalCost = totalCost + other.totalCost,
        upstreamCost = upstreamCost + other.upstreamCost,
        upstreamPromptCost = upstreamPromptCost + other.upstreamPromptCost,
        upstreamCompletionsCost = upstreamCompletionsCost + other.upstreamCompletionsCost,
    )
}

// RequestMetrics.kt
data class RequestMetrics(
    val tokens: TokenDetails = TokenDetails(),
    val cost: CostDetails = CostDetails(),
) {
    operator fun plus(other: RequestMetrics) = RequestMetrics(
        tokens = tokens + other.tokens,
        cost = cost + other.cost,
    )
}
```

### Turn — cleaned

```kotlin
data class Turn(
    val id: TurnId,
    val userMessage: String,
    val agentResponse: String,
    val timestamp: Instant = Clock.System.now(),
)
```

`TurnId` — value class wrapping String (UUID).

## Storage Layer

### Database schema

**TurnsTable** (modified — add `id`, remove token columns):

```kotlin
object TurnsTable : Table("turns") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val userMessage = text("user_message")
    val agentResponse = text("agent_response")
    val timestamp = long("timestamp")
}
```

**RequestMetricsTable** (new):

```kotlin
object RequestMetricsTable : Table("request_metrics") {
    val turnId = varchar("turn_id", 36)
    val promptTokens = integer("prompt_tokens")
    val completionTokens = integer("completion_tokens")
    val cachedTokens = integer("cached_tokens")
    val cacheWriteTokens = integer("cache_write_tokens")
    val reasoningTokens = integer("reasoning_tokens")
    val totalCost = double("total_cost")
    val upstreamCost = double("upstream_cost")
    val upstreamPromptCost = double("upstream_prompt_cost")
    val upstreamCompletionsCost = double("upstream_completions_cost")
}
```

### UsageManager

```kotlin
interface UsageManager {
    suspend fun record(turnId: TurnId, metrics: RequestMetrics)
    suspend fun getByTurn(turnId: TurnId): RequestMetrics?
    suspend fun getBySession(sessionId: SessionId): Map<TurnId, RequestMetrics>
    suspend fun getSessionTotal(sessionId: SessionId): RequestMetrics
}
```

`getBySession` and `getSessionTotal` perform a join with `TurnsTable` on `turnId` to filter by `sessionId`. `getBySession` returns a map keyed by `TurnId` so the UI can associate metrics with specific turns.

### SessionManager changes

- `appendTurn` generates and returns `TurnId`
- Token columns removed from read/write operations

## Agent Layer

### AgentResponse

```kotlin
data class AgentResponse(
    val text: String,
    val turnId: TurnId,
    val metrics: RequestMetrics,
)
```

### OpenRouterAgent

Mapping function:

```kotlin
private fun ChatResponse.toRequestMetrics(): RequestMetrics = RequestMetrics(
    tokens = TokenDetails(
        promptTokens = usage?.promptTokens ?: 0,
        completionTokens = usage?.completionTokens ?: 0,
        cachedTokens = usage?.promptTokensDetails?.cachedTokens ?: 0,
        cacheWriteTokens = usage?.promptTokensDetails?.cacheWriteTokens ?: 0,
        reasoningTokens = usage?.completionTokensDetails?.reasoningTokens ?: 0,
    ),
    cost = CostDetails(
        totalCost = usage?.cost ?: cost ?: 0.0,
        upstreamCost = costDetails?.upstreamCost ?: 0.0,
        upstreamPromptCost = costDetails?.upstreamPromptCost ?: 0.0,
        upstreamCompletionsCost = costDetails?.upstreamCompletionsCost ?: 0.0,
    ),
)
```

Coordination in `send()`:

```kotlin
val metrics = chatResponse.toRequestMetrics()
val turnId = sessionManager.appendTurn(sessionId, Turn(userMessage = message, agentResponse = text))
usageManager.record(turnId, metrics)
return AgentResponse(text = text, turnId = turnId, metrics = metrics).right()
```

Atomicity: two separate writes. Worst case — metrics lost for one turn, dialog preserved. Acceptable at current scale.

## UI Layer

### UiMessage — cleaned

```kotlin
data class UiMessage(
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val turnId: TurnId? = null,
)
```

### ChatStore.State

```kotlin
data class State(
    val sessionId: SessionId? = null,
    val messages: List<UiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val turnMetrics: Map<TurnId, RequestMetrics> = emptyMap(),
    val sessionMetrics: RequestMetrics = RequestMetrics(),
)
```

### Store Messages and Reducer

```kotlin
is Msg.AgentResponseMsg -> copy(
    messages = messages + UiMessage(text = msg.text, isUser = false, turnId = msg.turnId),
    turnMetrics = turnMetrics + (msg.turnId to msg.metrics),
    sessionMetrics = sessionMetrics + msg.metrics,
)
```

### Executor — LoadSession

Two independent data sources, can be parallelized with `async`:

```kotlin
val history = sessionManager.getHistory(sessionId)
val metricsList = usageManager.getBySession(sessionId)
```

### UI Display

**Per-turn (next to assistant bubble):**

```
↑128  ↓52  cached:96  reasoning:30  |  $0.0014
```

Only shown on assistant bubble. Zero values hidden.

**Session status bar:**

```
Session: ↑1,240  ↓385  cached:520  |  Total: $0.0156  |  Upstream: $0.0120
```

## Removed

- `TokenUsage.kt` — replaced by `TokenDetails` + `CostDetails` + `RequestMetrics`
- `UiMessage.tokenUsage` — replaced by `turnId` + lookup in `turnMetrics`
- `ChatStore.State.sessionTokens` — replaced by `sessionMetrics`
- Token columns in `TurnsTable`

## Testing

**llm-service:** parse full OpenRouter response with all optional fields present and absent.

**session-storage:** `TokenDetails.plus`, `CostDetails.plus`, `RequestMetrics.plus` accumulation; `ExposedUsageManager` CRUD and join-based session queries.

**ai-agent:** `ChatResponse.toRequestMetrics()` mapping; `send()` calls both managers; `AgentResponse` correctness.

**compose-ui:** reducer correctly populates `turnMetrics` and accumulates `sessionMetrics`; `SessionLoaded` loads from two sources; old `UiMessage.tokenUsage` tests removed.

## Migration

Current data is dev-only. Options:
1. Migrate existing token data from `turns` into `request_metrics`, generate `turnId` for existing rows, drop token columns.
2. Drop and recreate schema from scratch (loses dev history).
