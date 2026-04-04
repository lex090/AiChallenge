# Session Memory — Design Spec

## Goal

Agent persists chat history in SQLite and restores it on restart. Users can manage multiple sessions via a drawer UI.

## Architecture

### New module: `session-storage`

Package: `com.ai.challenge.session`

Dependencies: Exposed (Core + JDBC), SQLite JDBC, kotlinx-coroutines, kotlinx-datetime.

Module dependency graph:
```
compose-ui → ai-agent → session-storage
                      → llm-service
compose-ui → session-storage
```

### Domain Models

```kotlin
@JvmInline
value class SessionId(val value: String) {
    companion object {
        fun generate(): SessionId = SessionId(UUID.randomUUID().toString())
    }
}

data class Turn(
    val userMessage: String,
    val agentResponse: String,
    val timestamp: Instant = Clock.System.now()
)

data class AgentSession(
    val id: SessionId,
    val title: String = "",
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val history: List<Turn> = emptyList()
) {
    fun addTurn(turn: Turn): AgentSession =
        copy(history = history + turn, updatedAt = Clock.System.now())
}
```

### Interface: AgentSessionManager

```kotlin
interface AgentSessionManager {
    fun createSession(title: String = ""): SessionId
    fun getSession(id: SessionId): AgentSession?
    fun deleteSession(id: SessionId): Boolean
    fun listSessions(): List<AgentSession>
    fun getHistory(id: SessionId, limit: Int? = null): List<Turn>
    fun appendTurn(id: SessionId, turn: Turn)
    fun updateTitle(id: SessionId, title: String)
}
```

### Implementations

#### InMemorySessionManager

ConcurrentHashMap-based. Used for tests across all modules. Lives in `src/main/kotlin` (not test) so ai-agent and compose-ui tests can use it.

#### ExposedSessionManager

SQLite via Exposed. Two tables:

**SessionsTable:**
| Column     | Type         | Notes                    |
|------------|--------------|--------------------------|
| id         | varchar(36)  | PK, SessionId.value      |
| title      | varchar(255) | default ""               |
| created_at | long         | epochMillis              |
| updated_at | long         | epochMillis              |

**TurnsTable:**
| Column         | Type         | Notes                              |
|----------------|--------------|------------------------------------|
| id             | integer      | PK, autoIncrement                  |
| session_id     | varchar(36)  | FK → sessions.id, CASCADE delete   |
| user_message   | text         |                                    |
| agent_response | text         |                                    |
| timestamp      | long         | epochMillis                        |

Schema auto-created via `SchemaUtils.create()` on init.

DB file location: `~/.ai-challenge/sessions.db`

```kotlin
fun createSessionDatabase(): Database {
    val dbDir = Path(System.getProperty("user.home"), ".ai-challenge")
    dbDir.createDirectories()
    return Database.connect("jdbc:sqlite:${dbDir.resolve("sessions.db")}", driver = "org.sqlite.JDBC")
}
```

## Changes to `ai-agent`

### Agent interface

```kotlin
interface Agent {
    suspend fun send(sessionId: SessionId, message: String): Either<AgentError, String>
}
```

### OpenRouterAgent

Constructor adds `sessionManager: AgentSessionManager`.

`send()` logic:
1. Read history via `sessionManager.getHistory(sessionId)`
2. Build multi-turn request via ChatScope DSL: replay all turns as user/assistant pairs, then add new user message
3. Call LLM
4. On success: `sessionManager.appendTurn(sessionId, Turn(message, response))`, return Right
5. On failure: return Left (no turn saved)

## Changes to `compose-ui`

### ChatStore

```kotlin
interface ChatStore : Store<ChatStore.Intent, ChatStore.State, Nothing> {
    sealed interface Intent {
        data class SendMessage(val text: String) : Intent
        data class LoadSession(val sessionId: SessionId) : Intent
    }
    data class State(
        val sessionId: SessionId? = null,
        val messages: List<UiMessage> = emptyList(),
        val isLoading: Boolean = false,
    )
}
```

- `LoadSession`: loads history from SessionManager, converts `List<Turn>` to `List<UiMessage>` (each turn = 2 UiMessages: user + agent)
- `SendMessage`: calls `agent.send(sessionId, text)`. Auto-titles session on first message (first ~50 chars of user message) via `sessionManager.updateTitle()`.

### New: SessionListStore

```kotlin
interface SessionListStore : Store<SessionListStore.Intent, SessionListStore.State, Nothing> {
    sealed interface Intent {
        data object LoadSessions : Intent
        data object CreateSession : Intent
        data class DeleteSession(val id: SessionId) : Intent
    }
    data class State(
        val sessions: List<SessionItem> = emptyList(),
        val activeSessionId: SessionId? = null,
    )
    data class SessionItem(
        val id: SessionId,
        val title: String,
        val updatedAt: Instant,
    )
}
```

- `LoadSessions`: reads `sessionManager.listSessions()`, maps to `SessionItem`
- `CreateSession`: creates session, makes it active
- `DeleteSession`: deletes session; if active was deleted, switches to first remaining

### RootComponent

- Holds `SessionListStore` (lives at root level, independent of navigation)
- Holds callback `onSessionSelected(SessionId)` — creates/updates ChatComponent with new sessionId
- ChatComponent receives `sessionId` in constructor, sends `LoadSession` on init

### UI: Drawer

`RootContent` wraps in `ModalNavigationDrawer`:
- **Drawer content:** session list (title + date), "New chat" button at top
- **Main content:** current ChatContent
- Hamburger button in AppBar to open drawer

## DI (Koin)

```kotlin
val appModule = module {
    single { OpenRouterService(apiKey = System.getenv("OPENROUTER_API_KEY") ?: error("...")) }
    single<Database> { createSessionDatabase() }
    single<AgentSessionManager> { ExposedSessionManager(get()) }
    single<Agent> { OpenRouterAgent(get(), model = "google/gemini-2.0-flash-001", get()) }
}
```

## Testing

### session-storage tests
- In-memory SQLite (`jdbc:sqlite::memory:`)
- Cases: create/get/delete session, append/get history, getHistory with limit, listSessions ordering, updateTitle, CASCADE delete

### ai-agent tests
- Uses InMemorySessionManager
- FakeAgent updated: `send(sessionId, message)`
- OpenRouterAgentTest: verify turn saved on success, not saved on error, history sent as context (inspect MockEngine request body)

### compose-ui tests
- FakeAgent + InMemorySessionManager
- ChatStore: LoadSession loads history as UiMessages, SendMessage works with sessionId, auto-title on first message

## Edge Cases

- **First launch (no sessions):** App auto-creates a new empty session on startup if `listSessions()` returns empty.
- **All sessions deleted:** Same as first launch — auto-create new session.
- **Very long history:** No limit on history sent to LLM for now. Can add `getHistory(limit = N)` later if token limits become an issue.

## Data Flow

```
User clicks "New chat" in Drawer
  → SessionListStore.CreateSession
  → sessionManager.createSession() → SessionId
  → RootComponent.onSessionSelected(id)
  → ChatComponent created with sessionId
  → ChatStore.LoadSession(id) → empty history

User types message
  → ChatStore.SendMessage("hello")
  → agent.send(sessionId, "hello")
    → sessionManager.getHistory(sessionId) → []
    → LLM call: [user: "hello"]
    → sessionManager.appendTurn(sessionId, Turn("hello", "hi there"))
  → ChatStore state: [UiMessage("hello", user), UiMessage("hi there", agent)]
  → sessionManager.updateTitle(sessionId, "hello") // auto-title

App restart
  → SessionListStore.LoadSessions → shows "hello" session
  → User clicks session
  → ChatStore.LoadSession(id) → loads Turn from SQLite → UiMessages
  → User continues chatting with full context
```
