# Token Usage Display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Track and display token usage (prompt, completion, total) per message and per session, persisted in SQLite.

**Architecture:** Add `TokenUsage` data class in `session-storage`, `AgentResponse` wrapper in `ai-agent`, propagate through `Turn` → `ChatStore` → UI. OpenRouterAgent switches from `chatText()` to `chat()` to access full `ChatResponse` including `usage`.

**Tech Stack:** Kotlin, Exposed (SQLite), MVIKotlin, Compose Desktop, Arrow Either

---

### Task 1: Add TokenUsage data class to session-storage

**Files:**
- Create: `session-storage/src/main/kotlin/com/ai/challenge/session/TokenUsage.kt`

- [ ] **Step 1: Create TokenUsage data class**

```kotlin
package com.ai.challenge.session

data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :session-storage:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add session-storage/src/main/kotlin/com/ai/challenge/session/TokenUsage.kt
git commit -m "feat: add TokenUsage data class to session-storage"
```

---

### Task 2: Add tokenUsage field to Turn and persist in SQLite

**Files:**
- Modify: `session-storage/src/main/kotlin/com/ai/challenge/session/Turn.kt`
- Modify: `session-storage/src/main/kotlin/com/ai/challenge/session/ExposedSessionManager.kt`
- Test: `session-storage/src/test/kotlin/com/ai/challenge/session/ExposedSessionManagerTest.kt`

- [ ] **Step 1: Write failing test — token usage round-trip through ExposedSessionManager**

Add to `ExposedSessionManagerTest.kt`:

```kotlin
@Test
fun `appendTurn persists tokenUsage and getHistory restores it`() {
    val id = manager.createSession()
    val usage = TokenUsage(promptTokens = 100, completionTokens = 50, totalTokens = 150)
    manager.appendTurn(id, Turn(userMessage = "hi", agentResponse = "hello", tokenUsage = usage))

    val history = manager.getHistory(id)
    assertEquals(1, history.size)
    assertEquals(usage, history[0].tokenUsage)
}

@Test
fun `getHistory returns default TokenUsage for old records without token columns`() {
    val id = manager.createSession()
    manager.appendTurn(id, Turn(userMessage = "hi", agentResponse = "hello"))

    val history = manager.getHistory(id)
    assertEquals(1, history.size)
    assertEquals(TokenUsage(), history[0].tokenUsage)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :session-storage:test`
Expected: FAIL — `Turn` has no `tokenUsage` parameter

- [ ] **Step 3: Add tokenUsage to Turn**

Modify `session-storage/src/main/kotlin/com/ai/challenge/session/Turn.kt`:

```kotlin
package com.ai.challenge.session

import kotlin.time.Clock
import kotlin.time.Instant

data class Turn(
    val userMessage: String,
    val agentResponse: String,
    val timestamp: Instant = Clock.System.now(),
    val tokenUsage: TokenUsage = TokenUsage(),
)
```

- [ ] **Step 4: Add columns to TurnsTable and update ExposedSessionManager**

In `ExposedSessionManager.kt`, add three nullable columns to `TurnsTable`:

```kotlin
object TurnsTable : Table("turns") {
    val id = integer("id").autoIncrement()
    val sessionId = varchar("session_id", 36)
        .references(SessionsTable.id, onDelete = ReferenceOption.CASCADE)
    val userMessage = text("user_message")
    val agentResponse = text("agent_response")
    val timestamp = long("timestamp")
    val promptTokens = integer("prompt_tokens").nullable()
    val completionTokens = integer("completion_tokens").nullable()
    val totalTokens = integer("total_tokens").nullable()

    override val primaryKey = PrimaryKey(id)
}
```

Change `SchemaUtils.create` to `SchemaUtils.createMissingTablesAndColumns` in the `init` block:

```kotlin
init {
    transaction(database) {
        SchemaUtils.createMissingTablesAndColumns(SessionsTable, TurnsTable)
    }
}
```

Update `appendTurn` to write token columns:

```kotlin
override fun appendTurn(id: SessionId, turn: Turn) {
    val now = Clock.System.now()
    transaction(database) {
        TurnsTable.insert {
            it[sessionId] = id.value
            it[userMessage] = turn.userMessage
            it[agentResponse] = turn.agentResponse
            it[timestamp] = turn.timestamp.toEpochMilliseconds()
            it[promptTokens] = turn.tokenUsage.promptTokens
            it[completionTokens] = turn.tokenUsage.completionTokens
            it[totalTokens] = turn.tokenUsage.totalTokens
        }
        SessionsTable.update({ SessionsTable.id eq id.value }) {
            it[updatedAt] = now.toEpochMilliseconds()
        }
    }
}
```

Update `loadHistory` to read token columns:

```kotlin
private fun loadHistory(id: SessionId, limit: Int? = null): List<Turn> {
    val query = TurnsTable.selectAll()
        .where { TurnsTable.sessionId eq id.value }
        .orderBy(TurnsTable.timestamp, SortOrder.ASC)

    val rows = if (limit != null) {
        val allRows = query.toList()
        if (allRows.size > limit) allRows.takeLast(limit) else allRows
    } else {
        query.toList()
    }

    return rows.map { row ->
        Turn(
            userMessage = row[TurnsTable.userMessage],
            agentResponse = row[TurnsTable.agentResponse],
            timestamp = Instant.fromEpochMilliseconds(row[TurnsTable.timestamp]),
            tokenUsage = TokenUsage(
                promptTokens = row[TurnsTable.promptTokens] ?: 0,
                completionTokens = row[TurnsTable.completionTokens] ?: 0,
                totalTokens = row[TurnsTable.totalTokens] ?: 0,
            ),
        )
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :session-storage:test`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add session-storage/src/main/kotlin/com/ai/challenge/session/Turn.kt \
       session-storage/src/main/kotlin/com/ai/challenge/session/ExposedSessionManager.kt \
       session-storage/src/test/kotlin/com/ai/challenge/session/ExposedSessionManagerTest.kt
git commit -m "feat: persist token usage in TurnsTable"
```

---

### Task 3: Add AgentResponse and update Agent interface

**Files:**
- Create: `ai-agent/src/main/kotlin/com/ai/challenge/agent/AgentResponse.kt`
- Modify: `ai-agent/src/main/kotlin/com/ai/challenge/agent/Agent.kt`
- Modify: `ai-agent/src/main/kotlin/com/ai/challenge/agent/OpenRouterAgent.kt`
- Test: `ai-agent/src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt`

- [ ] **Step 1: Write failing test — token usage in agent response**

Add to `OpenRouterAgentTest.kt`:

```kotlin
@Test
fun `send returns AgentResponse with token usage`() = runTest {
    val service = createService("""
        {
          "choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}],
          "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}
        }
    """.trimIndent())
    val agent = OpenRouterAgent(service, model = "test-model", sessionManager = sessionManager)
    val sessionId = sessionManager.createSession()

    val result = agent.send(sessionId, "Hi")

    assertIs<Either.Right<AgentResponse>>(result)
    assertEquals("Hello!", result.value.text)
    assertEquals(TokenUsage(promptTokens = 10, completionTokens = 5, totalTokens = 15), result.value.tokenUsage)
}

@Test
fun `send returns default TokenUsage when usage is null`() = runTest {
    val service = createService("""
        {"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}
    """.trimIndent())
    val agent = OpenRouterAgent(service, model = "test-model", sessionManager = sessionManager)
    val sessionId = sessionManager.createSession()

    val result = agent.send(sessionId, "Hi")

    assertIs<Either.Right<AgentResponse>>(result)
    assertEquals(TokenUsage(), result.value.tokenUsage)
}

@Test
fun `send persists token usage in session turn`() = runTest {
    val service = createService("""
        {
          "choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}],
          "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}
        }
    """.trimIndent())
    val agent = OpenRouterAgent(service, model = "test-model", sessionManager = sessionManager)
    val sessionId = sessionManager.createSession()

    agent.send(sessionId, "Hi")

    val history = sessionManager.getHistory(sessionId)
    assertEquals(TokenUsage(promptTokens = 10, completionTokens = 5, totalTokens = 15), history[0].tokenUsage)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :ai-agent:test`
Expected: FAIL — `AgentResponse` does not exist, `Agent.send()` returns `String`

- [ ] **Step 3: Create AgentResponse**

Create `ai-agent/src/main/kotlin/com/ai/challenge/agent/AgentResponse.kt`:

```kotlin
package com.ai.challenge.agent

import com.ai.challenge.session.TokenUsage

data class AgentResponse(
    val text: String,
    val tokenUsage: TokenUsage,
)
```

- [ ] **Step 4: Update Agent interface**

Modify `ai-agent/src/main/kotlin/com/ai/challenge/agent/Agent.kt`:

```kotlin
package com.ai.challenge.agent

import arrow.core.Either
import com.ai.challenge.session.SessionId

interface Agent {
    suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse>
}
```

- [ ] **Step 5: Update OpenRouterAgent to use chat() and return AgentResponse**

Replace the `send` method in `OpenRouterAgent.kt`:

```kotlin
package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.TokenUsage
import com.ai.challenge.session.Turn

class OpenRouterAgent(
    private val service: OpenRouterService,
    private val model: String,
    private val sessionManager: AgentSessionManager,
) : Agent {

    override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> = either {
        val history = sessionManager.getHistory(sessionId)

        val chatResponse = catch({
            service.chat(model = model) {
                for (turn in history) {
                    user(turn.userMessage)
                    assistant(turn.agentResponse)
                }
                user(message)
            }
        }) { e: Exception ->
            val msg = e.message ?: "Unknown error"
            if (msg.startsWith("OpenRouter API error:")) {
                raise(AgentError.ApiError(msg.removePrefix("OpenRouter API error: ")))
            } else {
                raise(AgentError.NetworkError(msg))
            }
        }

        if (chatResponse.error != null) {
            raise(AgentError.ApiError(chatResponse.error.message ?: "Unknown API error"))
        }

        val text = chatResponse.choices.firstOrNull()?.message?.content
            ?: raise(AgentError.ApiError("Empty response from OpenRouter"))

        val tokenUsage = chatResponse.usage?.let {
            TokenUsage(
                promptTokens = it.promptTokens,
                completionTokens = it.completionTokens,
                totalTokens = it.totalTokens,
            )
        } ?: TokenUsage()

        sessionManager.appendTurn(sessionId, Turn(userMessage = message, agentResponse = text, tokenUsage = tokenUsage))

        AgentResponse(text = text, tokenUsage = tokenUsage)
    }
}
```

- [ ] **Step 6: Fix existing tests to work with AgentResponse**

Update existing tests in `OpenRouterAgentTest.kt`. The `send returns Right with response text on success` test needs updating:

```kotlin
@Test
fun `send returns Right with response text on success`() = runTest {
    val service = createService("""
        {"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}
    """.trimIndent())
    val agent = OpenRouterAgent(service, model = "test-model", sessionManager = sessionManager)
    val sessionId = sessionManager.createSession()

    val result = agent.send(sessionId, "Hi")

    assertIs<Either.Right<AgentResponse>>(result)
    assertEquals("Hello!", result.value.text)
}
```

Add import for `TokenUsage` at the top:

```kotlin
import com.ai.challenge.session.TokenUsage
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :ai-agent:test`
Expected: ALL PASS

- [ ] **Step 8: Commit**

```bash
git add ai-agent/src/main/kotlin/com/ai/challenge/agent/AgentResponse.kt \
       ai-agent/src/main/kotlin/com/ai/challenge/agent/Agent.kt \
       ai-agent/src/main/kotlin/com/ai/challenge/agent/OpenRouterAgent.kt \
       ai-agent/src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt
git commit -m "feat: Agent.send() returns AgentResponse with TokenUsage"
```

---

### Task 4: Update ChatStore to track and propagate token usage

**Files:**
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/model/UiMessage.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt`
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt`
- Test: `compose-ui/src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt`

- [ ] **Step 1: Write failing tests — token usage in ChatStore**

Add to `ChatStoreTest.kt`:

```kotlin
@Test
fun `SendMessage populates tokenUsage on user and agent messages`() = runTest {
    val sessionManager = InMemorySessionManager()
    val sessionId = sessionManager.createSession()
    val usage = TokenUsage(promptTokens = 100, completionTokens = 50, totalTokens = 150)
    val agent = FakeAgent(response = Either.Right(AgentResponse(text = "Hi!", tokenUsage = usage)))
    val store = ChatStoreFactory(DefaultStoreFactory(), agent, sessionManager).create()

    store.accept(ChatStore.Intent.LoadSession(sessionId))
    advanceUntilIdle()

    store.accept(ChatStore.Intent.SendMessage("Hello"))
    advanceUntilIdle()

    val messages = store.state.messages
    assertEquals(2, messages.size)
    assertEquals(usage, messages[0].tokenUsage)
    assertEquals(usage, messages[1].tokenUsage)
}

@Test
fun `SendMessage accumulates sessionTokens`() = runTest {
    val sessionManager = InMemorySessionManager()
    val sessionId = sessionManager.createSession()
    val usage = TokenUsage(promptTokens = 100, completionTokens = 50, totalTokens = 150)
    val agent = FakeAgent(response = Either.Right(AgentResponse(text = "Hi!", tokenUsage = usage)))
    val store = ChatStoreFactory(DefaultStoreFactory(), agent, sessionManager).create()

    store.accept(ChatStore.Intent.LoadSession(sessionId))
    advanceUntilIdle()

    store.accept(ChatStore.Intent.SendMessage("Hello"))
    advanceUntilIdle()

    assertEquals(usage, store.state.sessionTokens)
}

@Test
fun `LoadSession computes sessionTokens from history`() = runTest {
    val sessionManager = InMemorySessionManager()
    val sessionId = sessionManager.createSession()
    sessionManager.appendTurn(sessionId, Turn(userMessage = "a", agentResponse = "b", tokenUsage = TokenUsage(10, 5, 15)))
    sessionManager.appendTurn(sessionId, Turn(userMessage = "c", agentResponse = "d", tokenUsage = TokenUsage(20, 10, 30)))

    val store = ChatStoreFactory(DefaultStoreFactory(), FakeAgent(), sessionManager).create()

    store.accept(ChatStore.Intent.LoadSession(sessionId))
    advanceUntilIdle()

    assertEquals(TokenUsage(promptTokens = 30, completionTokens = 15, totalTokens = 45), store.state.sessionTokens)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :compose-ui:test`
Expected: FAIL — `UiMessage` has no `tokenUsage`, `ChatStore.State` has no `sessionTokens`

- [ ] **Step 3: Add tokenUsage to UiMessage**

Modify `compose-ui/src/main/kotlin/com/ai/challenge/ui/model/UiMessage.kt`:

```kotlin
package com.ai.challenge.ui.model

import com.ai.challenge.session.TokenUsage

data class UiMessage(
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val tokenUsage: TokenUsage = TokenUsage(),
)
```

- [ ] **Step 4: Add sessionTokens to ChatStore.State**

Modify `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt`:

```kotlin
package com.ai.challenge.ui.chat.store

import com.ai.challenge.session.SessionId
import com.ai.challenge.session.TokenUsage
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store

interface ChatStore : Store<ChatStore.Intent, ChatStore.State, Nothing> {

    sealed interface Intent {
        data class SendMessage(val text: String) : Intent
        data class LoadSession(val sessionId: SessionId) : Intent
    }

    data class State(
        val sessionId: SessionId? = null,
        val messages: List<UiMessage> = emptyList(),
        val isLoading: Boolean = false,
        val sessionTokens: TokenUsage = TokenUsage(),
    )
}
```

- [ ] **Step 5: Update ChatStoreFactory — Msg, Executor, Reducer**

Modify `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt`:

```kotlin
package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.agent.Agent
import com.ai.challenge.agent.AgentResponse
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.TokenUsage
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class ChatStoreFactory(
    private val storeFactory: StoreFactory,
    private val agent: Agent,
    private val sessionManager: AgentSessionManager,
) {
    fun create(): ChatStore =
        object : ChatStore,
            Store<ChatStore.Intent, ChatStore.State, Nothing> by storeFactory.create(
                name = "ChatStore",
                initialState = ChatStore.State(),
                executorFactory = { ExecutorImpl(agent, sessionManager) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class SessionLoaded(
            val sessionId: SessionId,
            val messages: List<UiMessage>,
            val sessionTokens: TokenUsage,
        ) : Msg
        data class UserMessage(val text: String) : Msg
        data class AgentResponseMsg(val text: String, val tokenUsage: TokenUsage) : Msg
        data class Error(val text: String) : Msg
        data object Loading : Msg
        data object LoadingComplete : Msg
    }

    private class ExecutorImpl(
        private val agent: Agent,
        private val sessionManager: AgentSessionManager,
    ) : CoroutineExecutor<ChatStore.Intent, Nothing, ChatStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: ChatStore.Intent) {
            when (intent) {
                is ChatStore.Intent.SendMessage -> handleSendMessage(intent.text)
                is ChatStore.Intent.LoadSession -> handleLoadSession(intent.sessionId)
            }
        }

        private fun handleLoadSession(sessionId: SessionId) {
            scope.launch {
                val history = sessionManager.getHistory(sessionId)
                val messages = history.flatMap { turn ->
                    listOf(
                        UiMessage(text = turn.userMessage, isUser = true, tokenUsage = turn.tokenUsage),
                        UiMessage(text = turn.agentResponse, isUser = false, tokenUsage = turn.tokenUsage),
                    )
                }
                val sessionTokens = history.fold(TokenUsage()) { acc, turn ->
                    TokenUsage(
                        promptTokens = acc.promptTokens + turn.tokenUsage.promptTokens,
                        completionTokens = acc.completionTokens + turn.tokenUsage.completionTokens,
                        totalTokens = acc.totalTokens + turn.tokenUsage.totalTokens,
                    )
                }
                dispatch(Msg.SessionLoaded(sessionId, messages, sessionTokens))
            }
        }

        private fun handleSendMessage(text: String) {
            val sessionId = state().sessionId ?: return
            dispatch(Msg.UserMessage(text))
            dispatch(Msg.Loading)

            scope.launch {
                when (val result = agent.send(sessionId, text)) {
                    is Either.Right -> dispatch(Msg.AgentResponseMsg(result.value.text, result.value.tokenUsage))
                    is Either.Left -> dispatch(Msg.Error(result.value.message))
                }
                dispatch(Msg.LoadingComplete)

                val session = sessionManager.getSession(sessionId)
                if (session != null && session.title.isEmpty()) {
                    val title = text.take(50)
                    sessionManager.updateTitle(sessionId, title)
                }
            }
        }
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<ChatStore.State, Msg> {
        override fun ChatStore.State.reduce(msg: Msg): ChatStore.State =
            when (msg) {
                is Msg.SessionLoaded -> copy(
                    sessionId = msg.sessionId,
                    messages = msg.messages,
                    sessionTokens = msg.sessionTokens,
                )
                is Msg.UserMessage -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = true),
                )
                is Msg.AgentResponseMsg -> {
                    val updatedUserMsg = messages.last().copy(tokenUsage = msg.tokenUsage)
                    copy(
                        messages = messages.dropLast(1) + updatedUserMsg + UiMessage(text = msg.text, isUser = false, tokenUsage = msg.tokenUsage),
                        sessionTokens = TokenUsage(
                            promptTokens = sessionTokens.promptTokens + msg.tokenUsage.promptTokens,
                            completionTokens = sessionTokens.completionTokens + msg.tokenUsage.completionTokens,
                            totalTokens = sessionTokens.totalTokens + msg.tokenUsage.totalTokens,
                        ),
                    )
                }
                is Msg.Error -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false, isError = true),
                )
                is Msg.Loading -> copy(isLoading = true)
                is Msg.LoadingComplete -> copy(isLoading = false)
            }
    }
}
```

Key design note: When `AgentResponseMsg` is reduced, we update the **last message** (the user message) with `tokenUsage` and add the agent message with the same `tokenUsage`. This way both messages in the pair carry the token data from the same API call.

- [ ] **Step 6: Update FakeAgent in test to return AgentResponse**

Update `FakeAgent` at the bottom of `ChatStoreTest.kt`:

```kotlin
class FakeAgent(
    private val response: Either<AgentError, AgentResponse> = Either.Right(AgentResponse(text = "", tokenUsage = TokenUsage())),
) : Agent {
    override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> = response
}
```

Update existing test that constructs `FakeAgent` with `Either.Right("Hello from agent!")`:

```kotlin
@Test
fun `SendMessage adds user message and agent response`() = runTest {
    val sessionManager = InMemorySessionManager()
    val sessionId = sessionManager.createSession()
    val agent = FakeAgent(response = Either.Right(AgentResponse(text = "Hello from agent!", tokenUsage = TokenUsage())))
    val store = ChatStoreFactory(DefaultStoreFactory(), agent, sessionManager).create()

    store.accept(ChatStore.Intent.LoadSession(sessionId))
    advanceUntilIdle()

    store.accept(ChatStore.Intent.SendMessage("Hi"))
    advanceUntilIdle()

    val messages = store.state.messages
    assertEquals(2, messages.size)
    assertEquals("Hi", messages[0].text)
    assertTrue(messages[0].isUser)
    assertEquals("Hello from agent!", messages[1].text)
    assertFalse(messages[1].isUser)
    assertFalse(store.state.isLoading)

    store.dispose()
}
```

Update `SendMessage adds error message on agent failure` test:

```kotlin
@Test
fun `SendMessage adds error message on agent failure`() = runTest {
    val sessionManager = InMemorySessionManager()
    val sessionId = sessionManager.createSession()
    val agent = FakeAgent(response = Either.Left(AgentError.NetworkError("Timeout")))
    val store = ChatStoreFactory(DefaultStoreFactory(), agent, sessionManager).create()

    store.accept(ChatStore.Intent.LoadSession(sessionId))
    advanceUntilIdle()

    store.accept(ChatStore.Intent.SendMessage("Hi"))
    advanceUntilIdle()

    val messages = store.state.messages
    assertEquals(2, messages.size)
    assertEquals("Hi", messages[0].text)
    assertTrue(messages[0].isUser)
    assertEquals("Timeout", messages[1].text)
    assertFalse(messages[1].isUser)
    assertTrue(messages[1].isError)
    assertFalse(store.state.isLoading)

    store.dispose()
}
```

Update `SendMessage auto-titles session on first message` test:

```kotlin
@Test
fun `SendMessage auto-titles session on first message`() = runTest {
    val sessionManager = InMemorySessionManager()
    val sessionId = sessionManager.createSession()
    val agent = FakeAgent(response = Either.Right(AgentResponse(text = "response", tokenUsage = TokenUsage())))
    val store = ChatStoreFactory(DefaultStoreFactory(), agent, sessionManager).create()

    store.accept(ChatStore.Intent.LoadSession(sessionId))
    advanceUntilIdle()

    store.accept(ChatStore.Intent.SendMessage("Hello world, this is a long message for auto-title testing purposes"))
    advanceUntilIdle()

    val title = sessionManager.getSession(sessionId)?.title ?: ""
    assertEquals("Hello world, this is a long message for auto-title", title)

    store.dispose()
}
```

Add necessary imports to top of `ChatStoreTest.kt`:

```kotlin
import com.ai.challenge.agent.AgentResponse
import com.ai.challenge.session.TokenUsage
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :compose-ui:test`
Expected: ALL PASS

- [ ] **Step 8: Commit**

```bash
git add compose-ui/src/main/kotlin/com/ai/challenge/ui/model/UiMessage.kt \
       compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt \
       compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt \
       compose-ui/src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt
git commit -m "feat: ChatStore tracks and propagates token usage"
```

---

### Task 5: Update ChatContent UI to display token usage

**Files:**
- Modify: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt`

- [ ] **Step 1: Add per-message token display to MessageBubble**

Update the `MessageBubble` composable in `ChatContent.kt`. Add a token label below the message text inside a `Column`:

```kotlin
@Composable
private fun MessageBubble(message: UiMessage) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        message.isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        message.isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 600.dp),
        ) {
            Text(
                text = message.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor)
                    .padding(12.dp),
                color = textColor,
            )
            if (message.tokenUsage.totalTokens > 0) {
                val tokenText = if (message.isUser) {
                    "\u2191${message.tokenUsage.promptTokens} tokens"
                } else {
                    "\u2193${message.tokenUsage.completionTokens} tokens"
                }
                Text(
                    text = tokenText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 2: Add session token status bar below input field**

In the `ChatContent` composable, add a `SessionTokenBar` between the message list and the input row. Update `ChatContent`:

```kotlin
@Composable
fun ChatContent(component: ChatComponent) {
    val state by component.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages) { message ->
                    MessageBubble(message)
                }
                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && inputText.isNotBlank() && !state.isLoading) {
                                component.onSendMessage(inputText.trim())
                                inputText = ""
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = { Text("Type a message...") },
                    enabled = !state.isLoading,
                    singleLine = true,
                )
                TextButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            component.onSendMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !state.isLoading,
                ) {
                    Text("Send")
                }
            }

            if (state.sessionTokens.totalTokens > 0) {
                SessionTokenBar(state.sessionTokens)
            }
    }
}
```

Add the `SessionTokenBar` composable:

```kotlin
@Composable
private fun SessionTokenBar(sessionTokens: com.ai.challenge.session.TokenUsage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Session: \u2191${sessionTokens.promptTokens} \u2193${sessionTokens.completionTokens} \u03A3${sessionTokens.totalTokens} tokens",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

Add missing import at the top of `ChatContent.kt`:

```kotlin
import com.ai.challenge.session.TokenUsage
```

- [ ] **Step 3: Verify full build compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt
git commit -m "feat: display token usage per message and session status bar"
```

---

### Task 6: Run full test suite and verify

- [ ] **Step 1: Run all tests**

Run: `./gradlew test`
Expected: ALL PASS across all modules

- [ ] **Step 2: Run the application manually**

Run: `OPENROUTER_API_KEY=<key> ./gradlew :compose-ui:run`

Verify:
- Send a message — token counts appear below user and agent messages
- Session status bar shows at the bottom with cumulative tokens
- Switch sessions — token counts load from history
- Create new session — no token bar visible until first message

- [ ] **Step 3: Final commit (if any fixes needed)**

```bash
git add -A
git commit -m "fix: address issues found during manual testing"
```
