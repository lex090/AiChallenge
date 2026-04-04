# Token Usage Display — Design Spec

## Overview

Add token usage tracking and display to the AI Agent Chat application. Track prompt tokens (sent), completion tokens (received), and total tokens per request and per session. Persist token data in SQLite alongside conversation history.

## Data Model

### TokenUsage (session-storage module)

```kotlin
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
)
```

Non-null everywhere. Default `(0, 0, 0)` for old records and fallback cases.

### AgentResponse (ai-agent module)

```kotlin
data class AgentResponse(
    val text: String,
    val tokenUsage: TokenUsage,
)
```

Replaces `String` as the success type in `Agent.send()`.

### Agent interface change

```kotlin
interface Agent {
    suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse>
}
```

### Turn extension (session-storage module)

```kotlin
data class Turn(
    val userMessage: String,
    val agentResponse: String,
    val timestamp: Instant = Clock.System.now(),
    val tokenUsage: TokenUsage = TokenUsage(),
)
```

### UiMessage extension (compose-ui module)

```kotlin
data class UiMessage(
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val tokenUsage: TokenUsage = TokenUsage(),
)
```

## Persistence

### TurnsTable — new columns

```kotlin
val promptTokens = integer("prompt_tokens").nullable()
val completionTokens = integer("completion_tokens").nullable()
val totalTokens = integer("total_tokens").nullable()
```

Columns are nullable in SQLite for backward compatibility. On read, null maps to `TokenUsage(0, 0, 0)`.

Migration: `SchemaUtils.createMissingTablesAndColumns()` adds columns automatically on startup.

### ExposedSessionManager changes

- `appendTurn()`: writes `tokenUsage.promptTokens`, `.completionTokens`, `.totalTokens` to new columns.
- `getHistory()`: reads columns, constructs `TokenUsage`. Null columns become 0.

### InMemorySessionManager

No changes needed — `Turn` already carries `tokenUsage`.

## Data Flow

```
OpenRouter API (ChatResponse.usage)
  → OpenRouterAgent maps Usage → TokenUsage, wraps in AgentResponse
  → Turn created with tokenUsage, persisted via SessionManager
  → ChatStore receives AgentResponse
    → Creates UiMessage for user (promptTokens from tokenUsage)
    → Creates UiMessage for agent (completionTokens from tokenUsage)
    → Updates sessionTokens sum in State
  → UI renders token info
```

## ChatStore Changes

### State

```kotlin
data class State(
    val sessionId: SessionId? = null,
    val messages: List<UiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val sessionTokens: TokenUsage = TokenUsage(),
)
```

### Behavior

- `LoadSession`: loads history, sums all `Turn.tokenUsage` into `sessionTokens`.
- `SendMessage`: on agent response, adds `tokenUsage` to `sessionTokens`.

## UI

### Per-message token display

Below each message, small text (`MaterialTheme.typography.labelSmall`, color `onSurfaceVariant`):

- User message: `↑128 tokens` (promptTokens from the Turn's tokenUsage)
- Agent message: `↓256 tokens` (completionTokens from the Turn's tokenUsage)

Hidden when `tokenUsage.totalTokens == 0` (old messages without tracking).

### Session status bar

`Row` below the input field:

```
Session: ↑1024 ↓2048 Σ3072 tokens
```

Sums of all promptTokens / completionTokens / totalTokens for the session. Hidden when `sessionTokens.totalTokens == 0`.

## Overflow Handling

Display-only approach. When the conversation exceeds the model's token limit, the API returns an error. This error is handled by existing `AgentError.ApiError` flow and displayed as an error message in the UI. No automatic truncation or preemptive warnings.

## Testing

### ai-agent tests

- `OpenRouterAgent` with `MockEngine`: verify `AgentResponse.tokenUsage` maps correctly from `ChatResponse.usage`.
- API response without `usage` field (null): verify default `TokenUsage(0, 0, 0)`.

### session-storage tests

- `ExposedSessionManager`: `appendTurn` with `tokenUsage`, then `getHistory` — verify tokens persist and load correctly.
- Old records without token columns — verify default `(0, 0, 0)`.

### compose-ui store tests

- `ChatStoreFactory`: after receiving agent response, `sessionTokens` accumulates correctly.
- `LoadSession`: session token sum computed from history.
