# Bounded Context Module Extraction — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Physically separate Bounded Contexts (Conversation, Context Management) into independent Gradle modules with explicit dependencies, enforced at compile time.

**Architecture:** Shared Kernel + Read Port. The monolithic `modules/core` is split into `shared-kernel` + `conversation/domain` + `context-management/domain`. Data and domain service modules are regrouped under their respective BC. New cross-context ports (`TurnQueryPort`, `ContextModeValidatorPort`) replace direct `AgentSessionRepository` usage across BC boundaries.

**Tech Stack:** Kotlin 2.3.20, Gradle 9.4.1, Arrow 2.1.2, Exposed 0.61.0, Ktor 3.4.2, Decompose 3.5.0, MVIKotlin 4.3.0, Koin 4.1.0

**Spec:** `docs/superpowers/specs/2026-04-12-bounded-context-modules-design.md`

**Package convention:** New modules use new root packages:
- `com.ai.challenge.sharedkernel` — shared-kernel
- `com.ai.challenge.conversation` — conversation/domain and conversation/data
- `com.ai.challenge.contextmanagement` — context-management/domain and context-management/data

---

## Overview

| Current Module | New Module | New Package Root |
|---|---|---|
| `modules/core` (shared types) | `modules/shared-kernel` | `com.ai.challenge.sharedkernel` |
| `modules/core` (conversation types) | `modules/conversation/domain` | `com.ai.challenge.conversation` |
| `modules/core` (CM types) | `modules/context-management/domain` | `com.ai.challenge.contextmanagement` |
| `modules/domain/ai-agent` | `modules/conversation/domain` (merged) | `com.ai.challenge.conversation.impl` |
| `modules/domain/context-manager` | `modules/context-management/domain` (merged) | `com.ai.challenge.contextmanagement.strategy` |
| `modules/domain/memory-service` | `modules/context-management/domain` (merged) | `com.ai.challenge.contextmanagement.memory` |
| `modules/data/session-repository-exposed` | `modules/conversation/data` | `com.ai.challenge.conversation.data` |
| `modules/data/memory-repository-exposed` | `modules/context-management/data` | `com.ai.challenge.contextmanagement.data` |
| `modules/data/open-router-service` | `modules/infrastructure/open-router-service` | `com.ai.challenge.infrastructure.llm` |

**Total tasks: 10** (execute sequentially)

---

### Task 1: Create shared-kernel module

Create the `modules/shared-kernel` Gradle module with all shared types. This is the foundation — every other module depends on it.

**Files to create:**
- `modules/shared-kernel/build.gradle.kts`
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/identity/AgentSessionId.kt`
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/identity/BranchId.kt`
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/identity/TurnId.kt`
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/model/MessageContent.kt`
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/model/CreatedAt.kt`
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/model/UpdatedAt.kt`
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/model/ContextModeId.kt` (NEW)
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/model/TurnSnapshot.kt` (NEW)
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/model/PreparedContext.kt`
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/model/ContextMessage.kt`
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/model/MessageRole.kt`
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/port/LlmPort.kt`
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/port/LlmResponse.kt`
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/port/ResponseFormat.kt`
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/port/TurnQueryPort.kt` (NEW)
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/port/ContextModeValidatorPort.kt` (NEW)
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/port/ContextManagerPort.kt` (renamed from ContextManager)
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/event/DomainEvent.kt`
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/event/DomainEventPublisher.kt`
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/event/DomainEventHandler.kt`
- `modules/shared-kernel/src/main/kotlin/com/ai/challenge/sharedkernel/error/DomainError.kt`

- [ ] **Step 1: Create build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.ai.challenge"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.arrow.core)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 2: Create identity types**

`AgentSessionId.kt`:
```kotlin
package com.ai.challenge.sharedkernel.identity

@JvmInline
value class AgentSessionId(val value: String)
```

`BranchId.kt`:
```kotlin
package com.ai.challenge.sharedkernel.identity

@JvmInline
value class BranchId(val value: String)
```

`TurnId.kt`:
```kotlin
package com.ai.challenge.sharedkernel.identity

@JvmInline
value class TurnId(val value: String)
```

- [ ] **Step 3: Create shared value objects**

`MessageContent.kt`:
```kotlin
package com.ai.challenge.sharedkernel.model

@JvmInline
value class MessageContent(val value: String)
```

`CreatedAt.kt`:
```kotlin
package com.ai.challenge.sharedkernel.model

import kotlinx.datetime.Instant

@JvmInline
value class CreatedAt(val value: Instant)
```

`UpdatedAt.kt`:
```kotlin
package com.ai.challenge.sharedkernel.model

import kotlinx.datetime.Instant

@JvmInline
value class UpdatedAt(val value: Instant)
```

Note: Check whether `CreatedAt`/`UpdatedAt` use `kotlinx.datetime.Instant` or `kotlin.time.Instant` from the original source. Read the original files to confirm. If they use `kotlin.time.Clock`/`kotlin.time.Instant` (Kotlin 2.3+), match that. Add `kotlinx-datetime` to build.gradle.kts dependencies only if the originals use it.

`ContextModeId.kt` (NEW):
```kotlin
package com.ai.challenge.sharedkernel.model

/**
 * Value Object — opaque identifier for a context management strategy.
 *
 * Stored in AgentSession (Conversation Context) as an opaque reference.
 * Interpreted by Context Management Context, which maps it to its
 * internal ContextManagementType enum.
 *
 * This design prevents Conversation from depending on CM-specific types.
 */
@JvmInline
value class ContextModeId(val value: String)
```

`TurnSnapshot.kt` (NEW):
```kotlin
package com.ai.challenge.sharedkernel.model

import com.ai.challenge.sharedkernel.identity.TurnId

/**
 * Value Object — read-only projection of a Turn for cross-context use.
 *
 * Returned by [TurnQueryPort]. Contains only the data Context Management
 * needs, without exposing Conversation aggregate internals (UsageRecord,
 * sessionId, etc.).
 */
data class TurnSnapshot(
    val turnId: TurnId,
    val userMessage: MessageContent,
    val assistantMessage: MessageContent,
)
```

`MessageRole.kt`:
```kotlin
package com.ai.challenge.sharedkernel.model

/**
 * Value Object — role of a message in LLM context.
 *
 * [System] — system prompt (instructions, facts, summaries).
 * [User] — user's message.
 * [Assistant] — LLM's response.
 */
enum class MessageRole {
    System,
    User,
    Assistant,
}
```

`ContextMessage.kt`:
```kotlin
package com.ai.challenge.sharedkernel.model

/**
 * Value Object — a single message in the prepared LLM context.
 *
 * Building block for [PreparedContext]. Pairs [MessageRole]
 * with [MessageContent] to represent system prompts,
 * user messages, and assistant responses.
 */
data class ContextMessage(
    val role: MessageRole,
    val content: MessageContent,
)
```

`PreparedContext.kt`:
```kotlin
package com.ai.challenge.sharedkernel.model

/**
 * Value Object — result of context preparation for an LLM call.
 *
 * Output of [ContextManagerPort]. Contains the ordered
 * list of [ContextMessage] ready to send to LLM, plus metadata
 * about compression applied.
 *
 * Immutable. Created once per message send cycle.
 */
data class PreparedContext(
    val messages: List<ContextMessage>,
    val compressed: Boolean,
    val originalTurnCount: Int,
    val retainedTurnCount: Int,
    val summaryCount: Int,
)
```

- [ ] **Step 4: Create ports**

`LlmPort.kt`:
```kotlin
package com.ai.challenge.sharedkernel.port

import arrow.core.Either
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.model.ContextMessage

/**
 * Port — domain boundary for LLM interactions.
 *
 * Anti-Corruption Layer: domain services depend on this interface,
 * not on any specific LLM provider.
 */
interface LlmPort {
    suspend fun complete(
        messages: List<ContextMessage>,
        responseFormat: ResponseFormat,
    ): Either<DomainError, LlmResponse>
}
```

`ResponseFormat.kt`:
```kotlin
package com.ai.challenge.sharedkernel.port

/**
 * Value Object — desired response format for LLM completions.
 */
sealed interface ResponseFormat {
    data object Text : ResponseFormat
    data object Json : ResponseFormat
}
```

`LlmResponse.kt`:
```kotlin
package com.ai.challenge.sharedkernel.port

import com.ai.challenge.sharedkernel.model.MessageContent

/**
 * Value Object — result of an LLM completion request.
 *
 * Note: UsageRecord is a Conversation-internal type, but LlmResponse
 * needs token/cost data. Define a lightweight LlmUsage here to avoid
 * depending on Conversation. The Conversation layer maps LlmUsage → UsageRecord.
 */
data class LlmResponse(
    val content: MessageContent,
    val usage: LlmUsage,
)

/**
 * Value Object — token/cost metrics from LLM response.
 * Shared Kernel version — lightweight, no Conversation dependencies.
 */
data class LlmUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalCost: java.math.BigDecimal,
)
```

Note: Check the original `LlmResponse` — it uses `UsageRecord` from `core.usage.model`. `UsageRecord` is a Conversation-internal type (contains `TokenCount`, `Cost`). For the shared kernel, we need a lightweight alternative. Read the original `UsageRecord`, `TokenCount`, `Cost` to determine the exact fields, then create `LlmUsage` that mirrors the relevant data. The Conversation layer will map `LlmUsage` → `UsageRecord` at its boundary.

`TurnQueryPort.kt` (NEW):
```kotlin
package com.ai.challenge.sharedkernel.port

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.model.TurnSnapshot

/**
 * Port — read-only access to conversation turns for Context Management.
 *
 * Allows Context Management to read turn data without depending
 * on Conversation aggregate internals. Implemented in conversation/data,
 * consumed by context-management/domain strategies.
 */
interface TurnQueryPort {
    suspend fun getTurnSnapshots(
        sessionId: AgentSessionId,
        branchId: BranchId,
    ): List<TurnSnapshot>
}
```

`ContextModeValidatorPort.kt` (NEW):
```kotlin
package com.ai.challenge.sharedkernel.port

import com.ai.challenge.sharedkernel.model.ContextModeId

/**
 * Port — validates context mode identifiers.
 *
 * Implemented by Context Management, called by application use cases
 * before saving a ContextModeId to AgentSession.
 */
fun interface ContextModeValidatorPort {
    fun isValid(contextModeId: ContextModeId): Boolean
}
```

`ContextManagerPort.kt` (renamed from ContextManager):
```kotlin
package com.ai.challenge.sharedkernel.port

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.model.ContextModeId
import com.ai.challenge.sharedkernel.model.MessageContent
import com.ai.challenge.sharedkernel.model.PreparedContext

/**
 * Port — context preparation for Conversation Context.
 *
 * Called before each LLM message send to prepare the conversation
 * context according to the session's context mode.
 *
 * Implemented in Context Management bounded context.
 * [contextModeId] is passed by the caller (Conversation) so that
 * the adapter does not need to query Conversation for session data.
 */
interface ContextManagerPort {
    suspend fun prepareContext(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        contextModeId: ContextModeId,
    ): PreparedContext
}
```

- [ ] **Step 5: Create event infrastructure**

`DomainEvent.kt`:
```kotlin
package com.ai.challenge.sharedkernel.event

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.model.TurnSnapshot

/**
 * Domain Event — fact of a change that occurred in the domain.
 *
 * Events are immutable and represent a completed action (past tense).
 * Used for communication between Bounded Contexts without creating
 * direct dependencies.
 */
sealed interface DomainEvent {

    data class TurnRecorded(
        val sessionId: AgentSessionId,
        val turnSnapshot: TurnSnapshot,
        val branchId: BranchId,
    ) : DomainEvent

    data class SessionCreated(
        val sessionId: AgentSessionId,
    ) : DomainEvent

    data class SessionDeleted(
        val sessionId: AgentSessionId,
    ) : DomainEvent
}
```

Note: `TurnRecorded` now carries `TurnSnapshot` instead of `Turn` — CM never sees the full Turn entity.

`DomainEventPublisher.kt`:
```kotlin
package com.ai.challenge.sharedkernel.event

interface DomainEventPublisher {
    suspend fun publish(event: DomainEvent)
}
```

`DomainEventHandler.kt`:
```kotlin
package com.ai.challenge.sharedkernel.event

interface DomainEventHandler<T : DomainEvent> {
    suspend fun handle(event: T)
}
```

- [ ] **Step 6: Create DomainError**

`DomainError.kt`:
```kotlin
package com.ai.challenge.sharedkernel.error

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.identity.TurnId
import com.ai.challenge.sharedkernel.model.ContextModeId

sealed interface DomainError {
    val message: String

    data class NetworkError(override val message: String) : DomainError
    data class ApiError(override val message: String) : DomainError
    data class DatabaseError(override val message: String) : DomainError

    data class SessionNotFound(val id: AgentSessionId) : DomainError {
        override val message: String get() = "Session not found: ${id.value}"
    }

    data class BranchNotFound(val id: BranchId) : DomainError {
        override val message: String get() = "Branch not found: ${id.value}"
    }

    data class TurnNotFound(val id: TurnId) : DomainError {
        override val message: String get() = "Turn not found: ${id.value}"
    }

    data class MainBranchCannotBeDeleted(val sessionId: AgentSessionId) : DomainError {
        override val message: String get() = "Cannot delete main branch for session: ${sessionId.value}"
    }

    data class BranchingNotEnabled(val sessionId: AgentSessionId) : DomainError {
        override val message: String get() = "Branching is not enabled for session: ${sessionId.value}"
    }

    data class BranchNotOwnedBySession(val branchId: BranchId, val sessionId: AgentSessionId) : DomainError {
        override val message: String get() = "Branch ${branchId.value} does not belong to session ${sessionId.value}"
    }

    data class UnknownContextMode(val contextModeId: ContextModeId) : DomainError {
        override val message: String get() = "Unknown context mode: ${contextModeId.value}"
    }
}
```

- [ ] **Step 7: Register module in settings.gradle.kts**

Add `include(":modules:shared-kernel")` under a new "Shared Kernel" comment section.

- [ ] **Step 8: Verify shared-kernel compiles**

Run: `./gradlew :modules:shared-kernel:compileKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add modules/shared-kernel/ settings.gradle.kts
git commit -m "feat: create shared-kernel module with identity types, shared VOs, ports, events, errors"
```

---

### Task 2: Create conversation/domain module

Move all Conversation Bounded Context domain types: aggregate (AgentSession, Branch, Turn), service interfaces, repository interface, use cases, and service implementations (from ai-agent).

**Files to create:**
- `modules/conversation/domain/build.gradle.kts`
- All Conversation domain model files (AgentSession, Branch, Turn, SessionTitle, TurnSequence, UsageRecord, TokenCount, Cost)
- Service interfaces (ChatService, SessionService, BranchService, UsageQueryService)
- Repository interface (AgentSessionRepository)
- Use case classes (SendMessageUseCase, CreateSessionUseCase, DeleteSessionUseCase, ApplicationInitService)
- Service implementations (AiChatService, AiSessionService, AiBranchService, AiUsageQueryService)

**Source mapping:**
- Models: copy from `modules/core/src/main/kotlin/com/ai/challenge/core/{session,branch,turn,chat/model,usage/model}/` → `modules/conversation/domain/src/main/kotlin/com/ai/challenge/conversation/`
- Services: copy from `modules/core/src/main/kotlin/com/ai/challenge/core/{chat,usage}/` → `modules/conversation/domain/.../service/`
- Use cases: copy from `modules/core/src/main/kotlin/com/ai/challenge/core/usecase/` → `modules/conversation/domain/.../usecase/`
- Implementations: copy from `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/` → `modules/conversation/domain/.../impl/`

- [ ] **Step 1: Create build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.ai.challenge"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":modules:shared-kernel"))
    implementation(libs.arrow.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 2: Create model files**

Create each model file under `modules/conversation/domain/src/main/kotlin/com/ai/challenge/conversation/model/`. Copy content from originals, update:
1. Package to `com.ai.challenge.conversation.model`
2. Imports to use `com.ai.challenge.sharedkernel.*` for shared types
3. `AgentSession.contextManagementType: ContextManagementType` → `contextModeId: ContextModeId`
4. `AgentSession.withContextManagementType(type)` → `withContextModeId(contextModeId: ContextModeId)`
5. Remove branching check from `AgentSession` — the branching-enabled check used `ContextManagementType.Branching` which is now a CM concept. Move this validation to the application use case layer where `ContextModeValidatorPort` is available.

Files to create:
- `AgentSession.kt` — update `contextManagementType` → `contextModeId: ContextModeId`, remove branching invariant (moves to use case)
- `Branch.kt` — update imports only
- `TurnSequence.kt` — update imports only
- `Turn.kt` — update imports only
- `SessionTitle.kt` — update package only (no shared-kernel imports needed)
- `UsageRecord.kt` — update package, keep `TokenCount` and `Cost` as separate files or in same file
- `TokenCount.kt` — update package
- `Cost.kt` — update package

Key change in `AgentSession.kt`:
```kotlin
package com.ai.challenge.conversation.model

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.model.ContextModeId
import com.ai.challenge.sharedkernel.model.CreatedAt
import com.ai.challenge.sharedkernel.model.UpdatedAt
import kotlin.time.Clock

data class AgentSession(
    val id: AgentSessionId,
    val title: SessionTitle,
    val contextModeId: ContextModeId,
    val createdAt: CreatedAt,
    val updatedAt: UpdatedAt,
) {
    fun withUpdatedTitle(newTitle: SessionTitle): AgentSession =
        copy(title = newTitle, updatedAt = UpdatedAt(value = Clock.System.now()))

    fun withContextModeId(contextModeId: ContextModeId): AgentSession =
        copy(contextModeId = contextModeId, updatedAt = UpdatedAt(value = Clock.System.now()))

    fun ensureBranchDeletable(branch: Branch): Either<DomainError, Unit> = either {
        ensure(!branch.isMain) {
            DomainError.MainBranchCannotBeDeleted(sessionId = id)
        }
    }
}
```

- [ ] **Step 3: Create service interfaces**

Copy from `modules/core/src/.../chat/{ChatService,SessionService,BranchService}.kt` and `modules/core/src/.../usage/UsageQueryService.kt`. Update package to `com.ai.challenge.conversation.service`, imports to shared-kernel and local model types.

- [ ] **Step 4: Create repository interface**

Copy `AgentSessionRepository.kt`. Update package to `com.ai.challenge.conversation.repository`, imports to use new model/identity packages.

- [ ] **Step 5: Create use case classes**

Copy from `modules/core/src/.../usecase/`. Update packages to `com.ai.challenge.conversation.usecase`, update imports.

Key change in `SendMessageUseCase`: `TurnRecorded` now takes `TurnSnapshot` instead of `Turn`:

```kotlin
// In SendMessageUseCase.execute():
val turnSnapshot = TurnSnapshot(
    turnId = turn.id,
    userMessage = turn.userMessage,
    assistantMessage = turn.assistantMessage,
)
eventPublisher.publish(
    event = DomainEvent.TurnRecorded(
        sessionId = sessionId,
        turnSnapshot = turnSnapshot,
        branchId = branchId,
    ),
)
```

- [ ] **Step 6: Create service implementations**

Copy from `modules/domain/ai-agent/src/.../agent/`. Update package to `com.ai.challenge.conversation.impl`, imports to new packages.

Key change in `AiChatService`: it uses `ContextManager` → now uses `ContextManagerPort`:
```kotlin
import com.ai.challenge.sharedkernel.port.ContextManagerPort

class AiChatService(
    private val llmPort: LlmPort,
    private val repository: AgentSessionRepository,
    private val contextManagerPort: ContextManagerPort,
) : ChatService {
    // ... update contextManager → contextManagerPort in method bodies
}
```

Note: `AiChatService` also creates `Turn` with `UsageRecord` from `LlmResponse.usage`. If `LlmResponse` now uses `LlmUsage` instead of `UsageRecord`, add a mapping:
```kotlin
val usageRecord = UsageRecord(
    promptTokens = TokenCount(value = response.usage.promptTokens),
    completionTokens = TokenCount(value = response.usage.completionTokens),
    totalCost = Cost(value = response.usage.totalCost),
)
```
Read the original `AiChatService` to confirm exact mapping.

- [ ] **Step 7: Register module in settings.gradle.kts**

Add `include(":modules:conversation:domain")`.

- [ ] **Step 8: Verify conversation/domain compiles**

Run: `./gradlew :modules:conversation:domain:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add modules/conversation/domain/ settings.gradle.kts
git commit -m "feat: create conversation/domain module with aggregate, services, use cases"
```

---

### Task 3: Create conversation/data module

Move `ExposedAgentSessionRepository` and create `ExposedTurnQueryAdapter`.

**Source:** `modules/data/session-repository-exposed/src/`

- [ ] **Step 1: Create build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.ai.challenge"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":modules:shared-kernel"))
    implementation(project(":modules:conversation:domain"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 2: Copy repository implementation files**

Copy all files from `modules/data/session-repository-exposed/src/main/kotlin/com/ai/challenge/session/repository/` to `modules/conversation/data/src/main/kotlin/com/ai/challenge/conversation/data/`.

Files: `ExposedAgentSessionRepository.kt`, `DatabaseFactory.kt`, `SessionsTable.kt`, `BranchesTable.kt`, `BranchTurnsTable.kt`, `TurnsTable.kt`.

Update all packages to `com.ai.challenge.conversation.data`, update imports.

Key change in `SessionsTable`: column `contextManagementType` → `contextModeId` (stored as String, maps to `ContextModeId`).

Key change in `ExposedAgentSessionRepository`: map `ContextModeId` ↔ DB string instead of `ContextManagementType`.

- [ ] **Step 3: Create ExposedTurnQueryAdapter**

```kotlin
package com.ai.challenge.conversation.data

import com.ai.challenge.conversation.repository.AgentSessionRepository
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.model.TurnSnapshot
import com.ai.challenge.sharedkernel.port.TurnQueryPort

/**
 * Adapter — implements TurnQueryPort by delegating to AgentSessionRepository.
 *
 * Maps Turn (Conversation internal) → TurnSnapshot (Shared Kernel).
 * This adapter is the only point where Conversation aggregate internals
 * are projected for cross-context consumption.
 */
class ExposedTurnQueryAdapter(
    private val repository: AgentSessionRepository,
) : TurnQueryPort {

    override suspend fun getTurnSnapshots(
        sessionId: AgentSessionId,
        branchId: BranchId,
    ): List<TurnSnapshot> {
        val turns = repository.getTurnsByBranch(branchId = branchId)
        return turns.map { turn ->
            TurnSnapshot(
                turnId = turn.id,
                userMessage = turn.userMessage,
                assistantMessage = turn.assistantMessage,
            )
        }
    }
}
```

- [ ] **Step 4: Copy tests**

Copy existing tests from `modules/data/session-repository-exposed/src/test/` to `modules/conversation/data/src/test/`, updating packages and imports.

- [ ] **Step 5: Register module and verify**

Add `include(":modules:conversation:data")` to settings.gradle.kts.

Run: `./gradlew :modules:conversation:data:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add modules/conversation/data/ settings.gradle.kts
git commit -m "feat: create conversation/data module with repository and TurnQueryAdapter"
```

---

### Task 4: Create context-management/domain module

Move all Context Management domain types: Fact, Summary, strategies, memory service, use cases, and event handlers. This is the largest migration task.

**Files to create from multiple sources:**
- From `modules/core/src/.../fact/` → Fact, FactCategory, FactKey, FactValue, FactRepository
- From `modules/core/src/.../summary/` → Summary, SummaryRepository
- From `modules/core/src/.../context/` → ContextManagementType, ContextStrategyConfig, ContextStrategy
- From `modules/core/src/.../context/model/` → SummaryContent, TurnIndex
- From `modules/core/src/.../memory/` → MemoryService, MemoryProvider, MemoryType, MemoryScope, MemorySnapshot, use cases
- From `modules/domain/context-manager/src/` → All strategies, ContextPreparationAdapter, compressor/extractor interfaces, ContextMessageMapper
- From `modules/domain/memory-service/src/` → DefaultMemoryService, providers, use case impls, SessionDeletedCleanupHandler

- [ ] **Step 1: Create build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.ai.challenge"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":modules:shared-kernel"))
    implementation(libs.arrow.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

Note: `kotlinx-serialization` is needed for `LlmFactExtractor` JSON parsing. However, `LlmFactExtractor` also uses `LlmPort` which makes it an infrastructure adapter, not domain. Read the spec again — `LlmContextCompressorAdapter` and `LlmFactExtractorAdapter` go to `context-management/data`. So serialization may not be needed here. Check whether any domain-level code uses serialization. If only the LLM adapters use it, move serialization dependency to `context-management/data` instead.

- [ ] **Step 2: Create CM domain models**

Create files under `modules/context-management/domain/src/main/kotlin/com/ai/challenge/contextmanagement/`:

Package structure:
```
com.ai.challenge.contextmanagement/
├── model/
│   ├── Fact.kt (+ FactCategory)
│   ├── FactKey.kt
│   ├── FactValue.kt
│   ├── Summary.kt
│   ├── SummaryContent.kt
│   ├── TurnIndex.kt
│   ├── ContextManagementType.kt
│   └── ContextStrategyConfig.kt
├── repository/
│   ├── FactRepository.kt
│   └── SummaryRepository.kt
├── memory/
│   ├── MemoryService.kt
│   ├── MemoryProvider.kt (+ FactMemoryProvider, SummaryMemoryProvider)
│   ├── MemoryType.kt
│   ├── MemoryScope.kt
│   └── MemorySnapshot.kt
├── usecase/
│   ├── GetMemoryUseCase.kt
│   ├── UpdateFactsUseCase.kt
│   ├── AddSummaryUseCase.kt
│   └── DeleteSummaryUseCase.kt
├── strategy/
│   ├── ContextStrategy.kt
│   ├── PassthroughStrategy.kt
│   ├── SlidingWindowStrategy.kt
│   ├── SummarizeOnThresholdStrategy.kt
│   ├── StickyFactsStrategy.kt
│   ├── BranchingContextManager.kt
│   ├── ContextPreparationAdapter.kt (renamed from ContextPreparationService)
│   ├── ContextCompressorPort.kt (renamed from ContextCompressor)
│   ├── FactExtractorPort.kt (renamed from FactExtractor)
│   ├── ContextModeValidatorAdapter.kt (NEW)
│   └── TurnSnapshotMapper.kt (replaces ContextMessageMapper, works with TurnSnapshot)
├── memory/impl/
│   ├── DefaultMemoryService.kt
│   ├── DefaultFactMemoryProvider.kt
│   ├── DefaultSummaryMemoryProvider.kt
│   └── SessionDeletedCleanupHandler.kt
└── usecase/impl/
    ├── DefaultGetMemoryUseCase.kt
    ├── DefaultUpdateFactsUseCase.kt
    ├── DefaultAddSummaryUseCase.kt
    └── DefaultDeleteSummaryUseCase.kt
```

Copy each file from its original location, update packages and imports.

- [ ] **Step 3: Refactor strategies to use TurnQueryPort**

All 5 strategies currently take `AgentSessionRepository` and call `getTurnsByBranch()`. Replace with `TurnQueryPort` and work with `TurnSnapshot` instead of `Turn`.

Example — `PassthroughStrategy.kt`:
```kotlin
package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.model.ContextMessage
import com.ai.challenge.sharedkernel.model.MessageContent
import com.ai.challenge.sharedkernel.model.MessageRole
import com.ai.challenge.sharedkernel.model.PreparedContext
import com.ai.challenge.sharedkernel.port.TurnQueryPort
import com.ai.challenge.contextmanagement.model.ContextStrategyConfig

class PassthroughStrategy(
    private val turnQueryPort: TurnQueryPort,
) : ContextStrategy {

    override suspend fun prepare(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        config: ContextStrategyConfig,
    ): PreparedContext {
        val snapshots = turnQueryPort.getTurnSnapshots(sessionId = sessionId, branchId = branchId)
        return PreparedContext(
            messages = snapshotsToMessages(snapshots = snapshots) + ContextMessage(role = MessageRole.User, content = newMessage),
            compressed = false,
            originalTurnCount = snapshots.size,
            retainedTurnCount = snapshots.size,
            summaryCount = 0,
        )
    }
}
```

Apply the same pattern to `SlidingWindowStrategy`, `BranchingContextManager`.

For `StickyFactsStrategy` and `SummarizeOnThresholdStrategy`: these also use `Turn` fields. Replace with `TurnSnapshot` fields (`userMessage`, `assistantMessage`). The `ContextCompressorPort` and `FactExtractorPort` interfaces also need updating — they currently take `List<Turn>`. Change to `List<TurnSnapshot>`.

`ContextCompressorPort.kt` (renamed):
```kotlin
package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.contextmanagement.model.SummaryContent
import com.ai.challenge.contextmanagement.model.Summary
import com.ai.challenge.sharedkernel.model.TurnSnapshot

interface ContextCompressorPort {
    suspend fun compress(turns: List<TurnSnapshot>, previousSummary: Summary?): SummaryContent
}
```

`FactExtractorPort.kt` (renamed):
```kotlin
package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.contextmanagement.model.Fact
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.model.MessageContent

interface FactExtractorPort {
    suspend fun extract(
        sessionId: AgentSessionId,
        currentFacts: List<Fact>,
        newUserMessage: MessageContent,
        lastAssistantResponse: MessageContent?,
    ): List<Fact>
}
```

- [ ] **Step 4: Create TurnSnapshotMapper**

Replaces `ContextMessageMapper` — works with `TurnSnapshot` instead of `Turn`:

```kotlin
package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.sharedkernel.model.ContextMessage
import com.ai.challenge.sharedkernel.model.MessageRole
import com.ai.challenge.sharedkernel.model.TurnSnapshot

internal fun snapshotsToMessages(snapshots: List<TurnSnapshot>): List<ContextMessage> =
    snapshots.flatMap {
        listOf(
            ContextMessage(role = MessageRole.User, content = it.userMessage),
            ContextMessage(role = MessageRole.Assistant, content = it.assistantMessage),
        )
    }
```

- [ ] **Step 5: Refactor ContextPreparationAdapter**

Renamed from `ContextPreparationService`. Key change: no longer takes `AgentSessionRepository` — uses `ContextModeId` lookup instead.

The orchestrator needs to know the session's `ContextModeId` to route to the correct strategy. Options:
1. Receive `contextModeId` as parameter (cleanest — no repository dependency)
2. Query through a new port

Since `ContextManagerPort.prepareContext()` currently only takes `sessionId`, the adapter needs a way to get the session's context mode. Add a `ContextModeQueryPort` to shared-kernel, or change the signature to pass `contextModeId`.

**Recommended: pass `contextModeId` as parameter.** `ContextManagerPort` already includes the `contextModeId` parameter (defined in Task 1).

`ContextPreparationAdapter`:
```kotlin
package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.contextmanagement.model.ContextManagementType
import com.ai.challenge.contextmanagement.model.ContextStrategyConfig
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.model.ContextModeId
import com.ai.challenge.sharedkernel.model.MessageContent
import com.ai.challenge.sharedkernel.model.PreparedContext
import com.ai.challenge.sharedkernel.port.ContextManagerPort

class ContextPreparationAdapter(
    private val strategies: Map<ContextManagementType, ContextStrategy>,
    private val configs: Map<ContextManagementType, ContextStrategyConfig>,
) : ContextManagerPort {

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        branchId: BranchId,
        newMessage: MessageContent,
        contextModeId: ContextModeId,
    ): PreparedContext {
        val type = ContextManagementType.fromModeId(contextModeId = contextModeId)
            ?: error("Unknown context mode: ${contextModeId.value}")
        val strategy = strategies[type] ?: error("No strategy for: $type")
        val config = configs[type] ?: error("No config for: $type")
        return strategy.prepare(sessionId = sessionId, branchId = branchId, newMessage = newMessage, config = config)
    }
}
```

Update `ContextManagementType` with mapping:
```kotlin
package com.ai.challenge.contextmanagement.model

import com.ai.challenge.sharedkernel.model.ContextModeId

sealed interface ContextManagementType {
    val modeId: ContextModeId

    data object None : ContextManagementType {
        override val modeId: ContextModeId = ContextModeId(value = "none")
    }
    data object SummarizeOnThreshold : ContextManagementType {
        override val modeId: ContextModeId = ContextModeId(value = "summarize_on_threshold")
    }
    data object SlidingWindow : ContextManagementType {
        override val modeId: ContextModeId = ContextModeId(value = "sliding_window")
    }
    data object StickyFacts : ContextManagementType {
        override val modeId: ContextModeId = ContextModeId(value = "sticky_facts")
    }
    data object Branching : ContextManagementType {
        override val modeId: ContextModeId = ContextModeId(value = "branching")
    }

    companion object {
        private val byModeId: Map<String, ContextManagementType> = listOf(
            None, SummarizeOnThreshold, SlidingWindow, StickyFacts, Branching,
        ).associateBy { it.modeId.value }

        fun fromModeId(contextModeId: ContextModeId): ContextManagementType? =
            byModeId[contextModeId.value]

        fun allModeIds(): List<ContextModeId> = byModeId.values.map { it.modeId }
    }
}
```

- [ ] **Step 6: Create ContextModeValidatorAdapter**

```kotlin
package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.contextmanagement.model.ContextManagementType
import com.ai.challenge.sharedkernel.model.ContextModeId
import com.ai.challenge.sharedkernel.port.ContextModeValidatorPort

class ContextModeValidatorAdapter : ContextModeValidatorPort {
    override fun isValid(contextModeId: ContextModeId): Boolean =
        ContextManagementType.fromModeId(contextModeId = contextModeId) != null
}
```

- [ ] **Step 7: Register module and verify**

Add `include(":modules:context-management:domain")` to settings.gradle.kts.

Run: `./gradlew :modules:context-management:domain:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add modules/context-management/domain/ settings.gradle.kts
git commit -m "feat: create context-management/domain module with models, strategies, memory service"
```

---

### Task 5: Create context-management/data module

Move memory repositories and LLM adapter implementations.

**Source:**
- `modules/data/memory-repository-exposed/src/` → ExposedFactRepository, ExposedSummaryRepository, MemoryDatabase, tables
- `modules/domain/context-manager/src/` → LlmContextCompressor, LlmFactExtractor (now adapters)

- [ ] **Step 1: Create build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.ai.challenge"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":modules:shared-kernel"))
    implementation(project(":modules:context-management:domain"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 2: Copy memory repository files**

Copy from `modules/data/memory-repository-exposed/src/` to `modules/context-management/data/src/main/kotlin/com/ai/challenge/contextmanagement/data/`.

Files: `ExposedFactRepository.kt`, `ExposedSummaryRepository.kt`, `MemoryDatabase.kt`, `FactsTable.kt`, `SummariesTable.kt`.

Update packages to `com.ai.challenge.contextmanagement.data`, update imports.

- [ ] **Step 3: Move LLM adapters**

Copy `LlmContextCompressor.kt` → `LlmContextCompressorAdapter.kt` and `LlmFactExtractor.kt` → `LlmFactExtractorAdapter.kt` from `modules/domain/context-manager/src/`.

Update:
- Package to `com.ai.challenge.contextmanagement.data`
- Class names to add `Adapter` suffix
- Implement renamed port interfaces (`ContextCompressorPort`, `FactExtractorPort`)
- Change `List<Turn>` parameters to `List<TurnSnapshot>` in compress method
- Update `turn.userMessage`/`turn.assistantMessage` → `snapshot.userMessage`/`snapshot.assistantMessage`

- [ ] **Step 4: Copy tests**

Copy existing tests, update packages and imports.

- [ ] **Step 5: Register module and verify**

Add `include(":modules:context-management:data")` to settings.gradle.kts.

Run: `./gradlew :modules:context-management:data:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add modules/context-management/data/ settings.gradle.kts
git commit -m "feat: create context-management/data module with repositories and LLM adapters"
```

---

### Task 6: Move infrastructure/open-router-service

Move `modules/data/open-router-service` to `modules/infrastructure/open-router-service` and update its dependency from `core` to `shared-kernel`.

- [ ] **Step 1: Copy module**

Copy entire `modules/data/open-router-service/` to `modules/infrastructure/open-router-service/`.

- [ ] **Step 2: Update build.gradle.kts**

Change:
```kotlin
implementation(project(":modules:core"))
```
to:
```kotlin
implementation(project(":modules:shared-kernel"))
```

- [ ] **Step 3: Update source files**

Update all imports from `com.ai.challenge.core.*` to `com.ai.challenge.sharedkernel.*`:
- `ContextMessage` → `com.ai.challenge.sharedkernel.model.ContextMessage`
- `MessageRole` → `com.ai.challenge.sharedkernel.model.MessageRole`
- `MessageContent` → `com.ai.challenge.sharedkernel.model.MessageContent`
- `LlmPort` → `com.ai.challenge.sharedkernel.port.LlmPort`
- `LlmResponse` → `com.ai.challenge.sharedkernel.port.LlmResponse`
- `ResponseFormat` → `com.ai.challenge.sharedkernel.port.ResponseFormat`
- `DomainError` → `com.ai.challenge.sharedkernel.error.DomainError`

Also update `OpenRouterAdapter` to create `LlmUsage` (shared-kernel) instead of `UsageRecord` (conversation-internal).

Optionally update package from `com.ai.challenge.llm` to `com.ai.challenge.infrastructure.llm`.

- [ ] **Step 4: Register module**

In settings.gradle.kts, replace:
```kotlin
include(":modules:data:open-router-service")
```
with:
```kotlin
include(":modules:infrastructure:open-router-service")
```

- [ ] **Step 5: Verify**

Run: `./gradlew :modules:infrastructure:open-router-service:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add modules/infrastructure/ settings.gradle.kts
git commit -m "refactor: move open-router-service to infrastructure module"
```

---

### Task 7: Update presentation/compose-ui

Update compose-ui to depend on new modules instead of old `core`.

- [ ] **Step 1: Update build.gradle.kts**

Replace:
```kotlin
implementation(project(":modules:core"))
```
with:
```kotlin
implementation(project(":modules:shared-kernel"))
implementation(project(":modules:conversation:domain"))
implementation(project(":modules:context-management:domain"))
```

- [ ] **Step 2: Update all imports in compose-ui source files**

Search and replace imports across all files in `modules/presentation/compose-ui/src/`:

| Old import prefix | New import prefix |
|---|---|
| `com.ai.challenge.core.session.AgentSessionId` | `com.ai.challenge.sharedkernel.identity.AgentSessionId` |
| `com.ai.challenge.core.session.AgentSession` | `com.ai.challenge.conversation.model.AgentSession` |
| `com.ai.challenge.core.branch.BranchId` | `com.ai.challenge.sharedkernel.identity.BranchId` |
| `com.ai.challenge.core.branch.Branch` | `com.ai.challenge.conversation.model.Branch` |
| `com.ai.challenge.core.turn.TurnId` | `com.ai.challenge.sharedkernel.identity.TurnId` |
| `com.ai.challenge.core.turn.Turn` | `com.ai.challenge.conversation.model.Turn` |
| `com.ai.challenge.core.chat.model.MessageContent` | `com.ai.challenge.sharedkernel.model.MessageContent` |
| `com.ai.challenge.core.chat.model.SessionTitle` | `com.ai.challenge.conversation.model.SessionTitle` |
| `com.ai.challenge.core.chat.ChatService` | `com.ai.challenge.conversation.service.ChatService` |
| `com.ai.challenge.core.chat.SessionService` | `com.ai.challenge.conversation.service.SessionService` |
| `com.ai.challenge.core.chat.BranchService` | `com.ai.challenge.conversation.service.BranchService` |
| `com.ai.challenge.core.usage.UsageQueryService` | `com.ai.challenge.conversation.service.UsageQueryService` |
| `com.ai.challenge.core.usage.model.*` | `com.ai.challenge.conversation.model.*` |
| `com.ai.challenge.core.usecase.*` | `com.ai.challenge.conversation.usecase.*` |
| `com.ai.challenge.core.context.ContextManagementType` | `com.ai.challenge.contextmanagement.model.ContextManagementType` |
| `com.ai.challenge.core.context.ContextMessage` | `com.ai.challenge.sharedkernel.model.ContextMessage` |
| `com.ai.challenge.core.error.DomainError` | `com.ai.challenge.sharedkernel.error.DomainError` |
| `com.ai.challenge.core.shared.CreatedAt` | `com.ai.challenge.sharedkernel.model.CreatedAt` |
| `com.ai.challenge.core.shared.UpdatedAt` | `com.ai.challenge.sharedkernel.model.UpdatedAt` |
| `com.ai.challenge.core.fact.*` | `com.ai.challenge.contextmanagement.model.*` |
| `com.ai.challenge.core.summary.*` | `com.ai.challenge.contextmanagement.model.*` |
| `com.ai.challenge.core.memory.usecase.*` | `com.ai.challenge.contextmanagement.usecase.*` |
| `com.ai.challenge.core.context.model.*` | `com.ai.challenge.contextmanagement.model.*` |

Also update any references to `contextManagementType` → `contextModeId` in UI stores/components.

- [ ] **Step 3: Verify**

Run: `./gradlew :modules:presentation:compose-ui:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add modules/presentation/compose-ui/
git commit -m "refactor: update compose-ui imports to new module structure"
```

---

### Task 8: Update presentation/app (DI and entry point)

Update the composition root — DI wiring, Main.kt, InProcessDomainEventPublisher.

- [ ] **Step 1: Update build.gradle.kts**

Replace old module dependencies:
```kotlin
dependencies {
    implementation(project(":modules:presentation:compose-ui"))
    implementation(project(":modules:shared-kernel"))
    implementation(project(":modules:conversation:domain"))
    implementation(project(":modules:conversation:data"))
    implementation(project(":modules:context-management:domain"))
    implementation(project(":modules:context-management:data"))
    implementation(project(":modules:infrastructure:open-router-service"))

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    implementation(libs.decompose)
    implementation(libs.mvikotlin)
    implementation(libs.mvikotlin.main)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 2: Update AppModule.kt**

Update all imports and add new bindings:

```kotlin
package com.ai.challenge.app.di

// Shared Kernel imports
import com.ai.challenge.sharedkernel.port.LlmPort
import com.ai.challenge.sharedkernel.port.TurnQueryPort
import com.ai.challenge.sharedkernel.port.ContextManagerPort
import com.ai.challenge.sharedkernel.port.ContextModeValidatorPort
import com.ai.challenge.sharedkernel.event.DomainEvent
import com.ai.challenge.sharedkernel.event.DomainEventPublisher

// Conversation imports
import com.ai.challenge.conversation.service.*
import com.ai.challenge.conversation.repository.AgentSessionRepository
import com.ai.challenge.conversation.usecase.*
import com.ai.challenge.conversation.impl.*
import com.ai.challenge.conversation.data.ExposedAgentSessionRepository
import com.ai.challenge.conversation.data.ExposedTurnQueryAdapter
import com.ai.challenge.conversation.data.createSessionDatabase

// Context Management imports
import com.ai.challenge.contextmanagement.model.ContextManagementType
import com.ai.challenge.contextmanagement.model.ContextStrategyConfig
import com.ai.challenge.contextmanagement.strategy.*
import com.ai.challenge.contextmanagement.memory.*
import com.ai.challenge.contextmanagement.memory.impl.*
import com.ai.challenge.contextmanagement.usecase.*
import com.ai.challenge.contextmanagement.usecase.impl.*
import com.ai.challenge.contextmanagement.repository.*
import com.ai.challenge.contextmanagement.data.*

// Infrastructure imports
import com.ai.challenge.llm.OpenRouterAdapter
import com.ai.challenge.llm.OpenRouterService

import com.ai.challenge.app.event.InProcessDomainEventPublisher
import org.koin.dsl.module

val appModule = module {
    // Infrastructure
    single {
        OpenRouterService(
            apiKey = System.getenv("OPENROUTER_API_KEY")
                ?: error("OPENROUTER_API_KEY environment variable is not set"),
        )
    }
    single<LlmPort> {
        OpenRouterAdapter(
            openRouterService = get(),
            model = "google/gemini-2.0-flash-001",
        )
    }

    // Conversation Context — Repositories
    single<AgentSessionRepository> { ExposedAgentSessionRepository(database = createSessionDatabase()) }

    // Cross-context ports
    single<TurnQueryPort> { ExposedTurnQueryAdapter(repository = get()) }

    // Context Management Context — Repositories
    single { createMemoryDatabase() }
    single<FactRepository> { ExposedFactRepository(database = get()) }
    single<SummaryRepository> { ExposedSummaryRepository(database = get()) }

    // Context Management — Memory
    single<FactMemoryProvider> { DefaultFactMemoryProvider(factRepository = get()) }
    single<SummaryMemoryProvider> { DefaultSummaryMemoryProvider(summaryRepository = get()) }
    single<MemoryService> { DefaultMemoryService(factMemoryProvider = get(), summaryMemoryProvider = get()) }

    // Context Management — Memory Use Cases
    single<GetMemoryUseCase> { DefaultGetMemoryUseCase(memoryService = get()) }
    single<UpdateFactsUseCase> { DefaultUpdateFactsUseCase(memoryService = get()) }
    single<AddSummaryUseCase> { DefaultAddSummaryUseCase(memoryService = get()) }
    single<DeleteSummaryUseCase> { DefaultDeleteSummaryUseCase(memoryService = get()) }

    // Context Management — Adapters
    single<ContextCompressorPort> { LlmContextCompressorAdapter(llmPort = get()) }
    single<FactExtractorPort> { LlmFactExtractorAdapter(llmPort = get()) }
    single<ContextModeValidatorPort> { ContextModeValidatorAdapter() }

    // Context Management — Strategies (now use TurnQueryPort)
    single { PassthroughStrategy(turnQueryPort = get()) }
    single { SlidingWindowStrategy(turnQueryPort = get()) }
    single {
        SummarizeOnThresholdStrategy(
            turnQueryPort = get(),
            compressor = get(),
            memoryService = get(),
        )
    }
    single {
        StickyFactsStrategy(
            turnQueryPort = get(),
            memoryService = get(),
            factExtractor = get(),
        )
    }
    single { BranchingContextManager(turnQueryPort = get()) }

    single<ContextManagerPort> {
        ContextPreparationAdapter(
            strategies = mapOf(
                ContextManagementType.None to get<PassthroughStrategy>() as ContextStrategy,
                ContextManagementType.SummarizeOnThreshold to get<SummarizeOnThresholdStrategy>() as ContextStrategy,
                ContextManagementType.SlidingWindow to get<SlidingWindowStrategy>() as ContextStrategy,
                ContextManagementType.StickyFacts to get<StickyFactsStrategy>() as ContextStrategy,
                ContextManagementType.Branching to get<BranchingContextManager>() as ContextStrategy,
            ),
            configs = mapOf(
                ContextManagementType.None to ContextStrategyConfig.None as ContextStrategyConfig,
                ContextManagementType.SummarizeOnThreshold to ContextStrategyConfig.SummarizeOnThreshold(
                    maxTurnsBeforeCompression = 15,
                    retainLastTurns = 5,
                    compressionInterval = 10,
                ) as ContextStrategyConfig,
                ContextManagementType.SlidingWindow to ContextStrategyConfig.SlidingWindow(
                    windowSize = 10,
                ) as ContextStrategyConfig,
                ContextManagementType.StickyFacts to ContextStrategyConfig.StickyFacts(
                    retainLastTurns = 5,
                ) as ContextStrategyConfig,
                ContextManagementType.Branching to ContextStrategyConfig.Branching as ContextStrategyConfig,
            ),
        )
    }

    // Conversation Context — Domain Services
    single<ChatService> { AiChatService(llmPort = get(), repository = get(), contextManagerPort = get()) }
    single<SessionService> { AiSessionService(repository = get()) }
    single<BranchService> { AiBranchService(repository = get()) }
    single<UsageQueryService> { AiUsageQueryService(repository = get()) }

    // Domain Events
    single { SessionDeletedCleanupHandler(memoryService = get()) }
    single<DomainEventPublisher> {
        InProcessDomainEventPublisher(
            handlers = mapOf(
                DomainEvent.SessionDeleted::class to listOf(get<SessionDeletedCleanupHandler>()),
            ),
        )
    }

    // Application Services (use cases)
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
}
```

- [ ] **Step 3: Update InProcessDomainEventPublisher imports**

Update to use `com.ai.challenge.sharedkernel.event.*`.

- [ ] **Step 4: Update Main.kt imports**

Update all `com.ai.challenge.core.*` imports to new packages.

- [ ] **Step 5: Verify**

Run: `./gradlew :modules:presentation:app:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add modules/presentation/app/
git commit -m "refactor: update app DI wiring for new module structure"
```

---

### Task 9: Delete old modules and cleanup

Remove the old module directories and their settings.gradle.kts registrations.

- [ ] **Step 1: Update settings.gradle.kts**

Replace old module includes with new:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "AiChallenge"

// Shared Kernel
include(":modules:shared-kernel")

// Conversation Bounded Context
include(":modules:conversation:domain")
include(":modules:conversation:data")

// Context Management Bounded Context
include(":modules:context-management:domain")
include(":modules:context-management:data")

// Infrastructure
include(":modules:infrastructure:open-router-service")

// Presentation
include(":modules:presentation:compose-ui")
include(":modules:presentation:app")

// Standalone
include(":modules:week1")
```

- [ ] **Step 2: Delete old module directories**

```bash
rm -rf modules/core
rm -rf modules/data/session-repository-exposed
rm -rf modules/data/memory-repository-exposed
rm -rf modules/data/open-router-service
rm -rf modules/domain/ai-agent
rm -rf modules/domain/context-manager
rm -rf modules/domain/memory-service
```

Also clean up empty parent directories:
```bash
rmdir modules/data 2>/dev/null || true
rmdir modules/domain 2>/dev/null || true
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: remove old module structure after BC extraction"
```

---

### Task 10: Full build verification and test

- [ ] **Step 1: Full build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 3: Run the application**

Run: `OPENROUTER_API_KEY=<key> ./gradlew :modules:presentation:app:run`
Expected: Application starts, UI renders, basic chat works

- [ ] **Step 4: Verify module dependency graph**

Run: `./gradlew :modules:context-management:domain:dependencies --configuration implementation`
Expected: Should show `shared-kernel` but NOT `conversation:domain`

Run: `./gradlew :modules:conversation:domain:dependencies --configuration implementation`
Expected: Should show `shared-kernel` but NOT `context-management:domain`

- [ ] **Step 5: Update CLAUDE.md**

Update the Architecture section in `CLAUDE.md` to reflect new module structure:
- Replace "Layer 0-2" descriptions with "Shared Kernel", "Conversation BC", "Context Management BC", "Infrastructure"
- Update module paths and dependency descriptions
- Add Port/Adapter naming convention

- [ ] **Step 6: Final commit**

```bash
git add -A
git commit -m "docs: update CLAUDE.md for new bounded context module structure"
```

---

## Notes for the implementer

1. **Package changes are the hardest part.** Use IDE refactoring (Move Package) where possible. If doing it manually, use search-and-replace per file.

2. **LlmResponse.usage:** The original `LlmResponse` uses `UsageRecord` (Conversation type). In shared-kernel, create a lightweight `LlmUsage` data class. `AiChatService` in conversation/domain maps `LlmUsage` → `UsageRecord` at its boundary. Read the original `UsageRecord`, `TokenCount`, `Cost` to get exact field names/types.

3. **ContextManagerPort signature change:** Adding `contextModeId` parameter to `prepareContext()` requires updating `AiChatService` (the caller). It must read the session's `contextModeId` and pass it. This is cleaner than having CM depend on Conversation to look up the session.

4. **Database migration:** The `sessions` table has a `context_management_type` column storing enum names. After the change, it stores `ContextModeId` string values. Ensure the `ExposedAgentSessionRepository` mapping is consistent. The simplest approach: keep storing the same string values (e.g., "None", "SlidingWindow") and use those as `ContextModeId.value`. Then `ContextManagementType.fromModeId()` maps them. Alternatively, use snake_case values ("none", "sliding_window") but then you need a DB migration.

5. **Branching check:** Currently `AiBranchService` checks `session.contextManagementType == ContextManagementType.Branching`. After refactoring, it would check `session.contextModeId == ContextModeId("branching")` — but this leaks CM knowledge into Conversation. Better approach: add a `ContextModeQueryPort` or pass the branching check to a use case that has access to `ContextModeValidatorPort` + a `isBranchingMode(contextModeId)` method. Or accept the pragmatic leak of a hardcoded string constant.
