# Context Management Type per Session — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-session context management type (sealed interface) with repository, strategy factory, and UI dialog for switching types.

**Architecture:** New sealed interface `ContextManagementType` in core with `None` and `SummarizeOnThreshold` variants. New `ContextManagementRepository` interface + Exposed implementation. `CompressionStrategy` renamed to `ContextStrategy`, `TurnCountStrategy` renamed to `SummarizeOnThresholdStrategy`. New `ContextStrategyFactory` creates strategies from types. `DefaultContextManager` loads type from repository instead of receiving strategy directly. UI: new `SessionSettingsDialog` opened from chat toolbar and drawer session row.

**Tech Stack:** Kotlin 2.3.20, Exposed 0.61.0, SQLite, Compose Desktop, Decompose 3.5.0, MVIKotlin 4.3.0, Arrow 2.1.2

---

### Task 1: Core — ContextManagementType sealed interface

**Files:**
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementType.kt`

- [ ] **Step 1: Create ContextManagementType**

```kotlin
package com.ai.challenge.core.context

sealed interface ContextManagementType {
    data object None : ContextManagementType
    data object SummarizeOnThreshold : ContextManagementType
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :modules:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementType.kt
git commit -m "feat: add ContextManagementType sealed interface"
```

---

### Task 2: Core — ContextManagementRepository interface

**Files:**
- Create: `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementRepository.kt`

- [ ] **Step 1: Create repository interface**

```kotlin
package com.ai.challenge.core.context

import com.ai.challenge.core.session.AgentSessionId

interface ContextManagementRepository {
    suspend fun save(sessionId: AgentSessionId, type: ContextManagementType)
    suspend fun getBySession(sessionId: AgentSessionId): ContextManagementType
    suspend fun delete(sessionId: AgentSessionId)
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :modules:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementRepository.kt
git commit -m "feat: add ContextManagementRepository interface"
```

---

### Task 3: Core — Rename CompressionStrategy to ContextStrategy

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/context/CompressionStrategy.kt`

- [ ] **Step 1: Rename interface**

Rename `CompressionStrategy` to `ContextStrategy` in `CompressionStrategy.kt`. The file content becomes:

```kotlin
package com.ai.challenge.core.context

sealed interface CompressionDecision {
    data object Skip : CompressionDecision
    data class Compress(val partitionPoint: Int) : CompressionDecision
}

interface ContextStrategy {
    fun evaluate(context: CompressionContext): CompressionDecision
}
```

- [ ] **Step 2: Rename the file**

Rename file from `CompressionStrategy.kt` to `ContextStrategy.kt`:

```bash
git mv modules/core/src/main/kotlin/com/ai/challenge/core/context/CompressionStrategy.kt modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextStrategy.kt
```

- [ ] **Step 3: Update DefaultContextManager import**

In `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt`, change:

```kotlin
import com.ai.challenge.core.context.CompressionStrategy
```

to:

```kotlin
import com.ai.challenge.core.context.ContextStrategy
```

And change the constructor parameter type:

```kotlin
private val strategy: CompressionStrategy,
```

to:

```kotlin
private val strategy: ContextStrategy,
```

- [ ] **Step 4: Update TurnCountStrategy**

In `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/TurnCountStrategy.kt`, change:

```kotlin
import com.ai.challenge.core.context.CompressionStrategy
```

to:

```kotlin
import com.ai.challenge.core.context.ContextStrategy
```

And change:

```kotlin
) : CompressionStrategy {
```

to:

```kotlin
) : ContextStrategy {
```

- [ ] **Step 5: Update AppModule**

In `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt`, change:

```kotlin
import com.ai.challenge.core.context.CompressionStrategy
```

to:

```kotlin
import com.ai.challenge.core.context.ContextStrategy
```

And change:

```kotlin
single<CompressionStrategy> { TurnCountStrategy(maxTurns = 15, retainLast = 5, compressionInterval = 10) }
```

to:

```kotlin
single<ContextStrategy> { TurnCountStrategy(maxTurns = 15, retainLast = 5, compressionInterval = 10) }
```

- [ ] **Step 6: Update DefaultContextManagerTest**

In `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt`, the `createManager` method passes `TurnCountStrategy` which already returns `ContextStrategy`. No import of `CompressionStrategy` is used in the test — verify no changes needed.

- [ ] **Step 7: Verify everything compiles and tests pass**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: rename CompressionStrategy to ContextStrategy"
```

---

### Task 4: Domain — Rename TurnCountStrategy to SummarizeOnThresholdStrategy

**Files:**
- Modify: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/TurnCountStrategy.kt`
- Modify: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/TurnCountStrategyTest.kt`
- Modify: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt`
- Modify: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt`

- [ ] **Step 1: Rename the class and file**

In `TurnCountStrategy.kt`, rename class to `SummarizeOnThresholdStrategy`:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.context.CompressionContext
import com.ai.challenge.core.context.CompressionDecision
import com.ai.challenge.core.context.ContextStrategy

class SummarizeOnThresholdStrategy(
    private val maxTurns: Int,
    private val retainLast: Int,
    private val compressionInterval: Int,
) : ContextStrategy {

    override fun evaluate(context: CompressionContext): CompressionDecision {
        val shouldCompress = when (val lastIndex = context.lastSummary?.toTurnIndex) {
            null -> context.history.size >= maxTurns
            else -> context.history.size - lastIndex >= retainLast + compressionInterval
        }

        if (!shouldCompress) return CompressionDecision.Skip

        val partitionPoint = (context.history.size - retainLast).coerceAtLeast(0)
        return CompressionDecision.Compress(partitionPoint)
    }
}
```

Then rename the file:

```bash
git mv modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/TurnCountStrategy.kt modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/SummarizeOnThresholdStrategy.kt
```

Note: remove the default value `compressionInterval: Int = maxTurns - retainLast` — per project rules, no default parameter values. All call sites must pass explicitly.

- [ ] **Step 2: Rename the test file and update references**

Rename `TurnCountStrategyTest.kt` to `SummarizeOnThresholdStrategyTest.kt` and update all references inside:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.context.CompressionContext
import com.ai.challenge.core.context.CompressionDecision
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.turn.Turn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SummarizeOnThresholdStrategyTest {

    private fun turns(count: Int): List<Turn> =
        (1..count).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") }

    private fun context(turnCount: Int, lastSummary: Summary? = null) =
        CompressionContext(history = turns(turnCount), lastSummary = lastSummary)

    private fun summaryAt(toTurnIndex: Int) =
        Summary(text = "summary", fromTurnIndex = 0, toTurnIndex = toTurnIndex)

    @Test
    fun `returns Skip when no prior compression and history below maxTurns`() {
        val strategy = SummarizeOnThresholdStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 3)
        assertIs<CompressionDecision.Skip>(strategy.evaluate(context(3)))
    }

    @Test
    fun `returns Skip when no prior compression and empty history`() {
        val strategy = SummarizeOnThresholdStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 3)
        assertIs<CompressionDecision.Skip>(strategy.evaluate(context(0)))
    }

    @Test
    fun `returns Skip when not enough turns accumulated after compression`() {
        val strategy = SummarizeOnThresholdStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 5)
        assertIs<CompressionDecision.Skip>(strategy.evaluate(context(8, summaryAt(3))))
    }

    @Test
    fun `returns Compress with correct partitionPoint at maxTurns`() {
        val strategy = SummarizeOnThresholdStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 3)
        val decision = strategy.evaluate(context(5))
        assertIs<CompressionDecision.Compress>(decision)
        assertEquals(3, decision.partitionPoint)
    }

    @Test
    fun `returns Compress when history exceeds maxTurns`() {
        val strategy = SummarizeOnThresholdStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 3)
        val decision = strategy.evaluate(context(6))
        assertIs<CompressionDecision.Compress>(decision)
        assertEquals(4, decision.partitionPoint)
    }

    @Test
    fun `returns Compress when enough turns after prior compression`() {
        val strategy = SummarizeOnThresholdStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 5)
        val decision = strategy.evaluate(context(11, summaryAt(3)))
        assertIs<CompressionDecision.Compress>(decision)
        assertEquals(9, decision.partitionPoint)
    }

    @Test
    fun `returns Compress when exactly at threshold after compression`() {
        val strategy = SummarizeOnThresholdStrategy(maxTurns = 5, retainLast = 2, compressionInterval = 5)
        val decision = strategy.evaluate(context(10, summaryAt(3)))
        assertIs<CompressionDecision.Compress>(decision)
        assertEquals(8, decision.partitionPoint)
    }

    @Test
    fun `partitionPoint is 0 when retainLast exceeds history size`() {
        val strategy = SummarizeOnThresholdStrategy(maxTurns = 5, retainLast = 20, compressionInterval = 3)
        val decision = strategy.evaluate(context(6))
        assertIs<CompressionDecision.Compress>(decision)
        assertEquals(0, decision.partitionPoint)
    }
}
```

```bash
git mv modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/TurnCountStrategyTest.kt modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/SummarizeOnThresholdStrategyTest.kt
```

- [ ] **Step 3: Update DefaultContextManagerTest**

In `DefaultContextManagerTest.kt`, update `createManager`:

```kotlin
    private fun createManager(
        maxTurns: Int,
        retainLast: Int,
        compressionInterval: Int,
    ): DefaultContextManager =
        DefaultContextManager(
            strategy = SummarizeOnThresholdStrategy(
                maxTurns = maxTurns,
                retainLast = retainLast,
                compressionInterval = compressionInterval,
            ),
            compressor = fakeCompressor,
            summaryRepository = fakeSummaryRepo,
        )
```

Note: remove default parameter values from `createManager` — update all call sites in the test to pass all three arguments explicitly. Existing calls:

- `createManager(maxTurns = 5, retainLast = 2)` → `createManager(maxTurns = 5, retainLast = 2, compressionInterval = 3)`
- `createManager(maxTurns = 5, retainLast = 2, compressionInterval = 3)` — already explicit

- [ ] **Step 4: Update AppModule**

In `AppModule.kt`, change:

```kotlin
import com.ai.challenge.context.TurnCountStrategy
```

to:

```kotlin
import com.ai.challenge.context.SummarizeOnThresholdStrategy
```

And change:

```kotlin
single<ContextStrategy> { TurnCountStrategy(maxTurns = 15, retainLast = 5, compressionInterval = 10) }
```

to:

```kotlin
single<ContextStrategy> { SummarizeOnThresholdStrategy(maxTurns = 15, retainLast = 5, compressionInterval = 10) }
```

- [ ] **Step 5: Verify everything compiles and tests pass**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: rename TurnCountStrategy to SummarizeOnThresholdStrategy"
```

---

### Task 5: Domain — ContextStrategyFactory

**Files:**
- Create: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/ContextStrategyFactory.kt`
- Create: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/ContextStrategyFactoryTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.context.CompressionContext
import com.ai.challenge.core.context.CompressionDecision
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.turn.Turn
import kotlin.test.Test
import kotlin.test.assertIs

class ContextStrategyFactoryTest {

    private val factory = ContextStrategyFactory()

    @Test
    fun `None type creates strategy that always skips`() {
        val strategy = factory.create(ContextManagementType.None)
        val context = CompressionContext(
            history = (1..100).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") },
            lastSummary = null,
        )
        assertIs<CompressionDecision.Skip>(strategy.evaluate(context))
    }

    @Test
    fun `SummarizeOnThreshold type creates strategy that compresses at threshold`() {
        val strategy = factory.create(ContextManagementType.SummarizeOnThreshold)
        val context = CompressionContext(
            history = (1..20).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") },
            lastSummary = null,
        )
        assertIs<CompressionDecision.Compress>(strategy.evaluate(context))
    }

    @Test
    fun `SummarizeOnThreshold type skips when below threshold`() {
        val strategy = factory.create(ContextManagementType.SummarizeOnThreshold)
        val context = CompressionContext(
            history = (1..3).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") },
            lastSummary = null,
        )
        assertIs<CompressionDecision.Skip>(strategy.evaluate(context))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :modules:domain:context-manager:test --tests "com.ai.challenge.context.ContextStrategyFactoryTest"`
Expected: FAIL — `ContextStrategyFactory` not found

- [ ] **Step 3: Implement ContextStrategyFactory**

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextStrategy
import com.ai.challenge.core.context.CompressionContext
import com.ai.challenge.core.context.CompressionDecision

class ContextStrategyFactory {
    fun create(type: ContextManagementType): ContextStrategy =
        when (type) {
            is ContextManagementType.None -> NoneContextStrategy
            is ContextManagementType.SummarizeOnThreshold -> SummarizeOnThresholdStrategy(
                maxTurns = 15,
                retainLast = 5,
                compressionInterval = 10,
            )
        }
}

private object NoneContextStrategy : ContextStrategy {
    override fun evaluate(context: CompressionContext): CompressionDecision = CompressionDecision.Skip
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :modules:domain:context-manager:test`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/ContextStrategyFactory.kt modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/ContextStrategyFactoryTest.kt
git commit -m "feat: add ContextStrategyFactory"
```

---

### Task 6: Domain — Update DefaultContextManager to use repository + factory

**Files:**
- Modify: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt`
- Modify: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt`
- Modify: `modules/domain/context-manager/build.gradle.kts` (no change needed — already depends on core)

- [ ] **Step 1: Update the test to use factory + in-memory repository**

Replace the full content of `DefaultContextManagerTest.kt`:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.context.ContextCompressor
import com.ai.challenge.core.context.ContextManagementRepository
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.turn.Turn
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultContextManagerTest {

    private fun turns(count: Int): List<Turn> =
        (1..count).map { Turn(userMessage = "msg$it", agentResponse = "resp$it") }

    private lateinit var fakeCompressor: FakeContextCompressor
    private lateinit var fakeSummaryRepo: InMemorySummaryRepository
    private lateinit var fakeContextManagementRepo: InMemoryContextManagementRepository

    @BeforeTest
    fun setup() {
        fakeCompressor = FakeContextCompressor()
        fakeSummaryRepo = InMemorySummaryRepository()
        fakeContextManagementRepo = InMemoryContextManagementRepository()
    }

    private fun createManager(): DefaultContextManager =
        DefaultContextManager(
            contextManagementRepository = fakeContextManagementRepo,
            strategyFactory = ContextStrategyFactory(),
            compressor = fakeCompressor,
            summaryRepository = fakeSummaryRepo,
        )

    @Test
    fun `returns all turns when type is None`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId, ContextManagementType.None)
        val manager = createManager()
        val history = turns(20)

        val result = manager.prepareContext(sessionId, history, "new msg")

        assertFalse(result.compressed)
        assertEquals(20, result.originalTurnCount)
        assertEquals(20, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
    }

    @Test
    fun `returns all turns when SummarizeOnThreshold and below threshold`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId, ContextManagementType.SummarizeOnThreshold)
        val manager = createManager()
        val history = turns(3)

        val result = manager.prepareContext(sessionId, history, "new msg")

        assertFalse(result.compressed)
        assertEquals(3, result.originalTurnCount)
        assertEquals(3, result.retainedTurnCount)
        assertEquals(0, result.summaryCount)
        assertEquals(7, result.messages.size)
        assertEquals(ContextMessage(MessageRole.User, "new msg"), result.messages.last())
    }

    @Test
    fun `compresses when SummarizeOnThreshold and at threshold`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId, ContextManagementType.SummarizeOnThreshold)
        val manager = createManager()
        val history = turns(15)

        val result = manager.prepareContext(sessionId, history, "new msg")

        assertTrue(result.compressed)
        assertEquals(15, result.originalTurnCount)
        assertEquals(5, result.retainedTurnCount)
        assertEquals(1, result.summaryCount)
        assertEquals(MessageRole.System, result.messages.first().role)
        assertEquals(1, fakeCompressor.callCount)
    }

    @Test
    fun `reuses existing summary without recompressing during interval`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId, ContextManagementType.SummarizeOnThreshold)
        val manager = createManager()

        manager.prepareContext(sessionId, turns(15), "msg15")
        assertEquals(1, fakeCompressor.callCount)

        val result = manager.prepareContext(sessionId, turns(16), "msg16")
        assertEquals(1, fakeCompressor.callCount)
        assertTrue(result.compressed)
    }

    @Test
    fun `handles empty history`() = runTest {
        val sessionId = AgentSessionId("s1")
        fakeContextManagementRepo.save(sessionId, ContextManagementType.SummarizeOnThreshold)
        val manager = createManager()

        val result = manager.prepareContext(sessionId, emptyList(), "hello")

        assertFalse(result.compressed)
        assertEquals(0, result.originalTurnCount)
        assertEquals(1, result.messages.size)
        assertEquals(ContextMessage(MessageRole.User, "hello"), result.messages[0])
    }
}

private class FakeContextCompressor : ContextCompressor {
    var callCount = 0
        private set
    var lastPreviousSummary: Summary? = null
        private set
    var lastTurnCount = 0
        private set

    override suspend fun compress(turns: List<Turn>, previousSummary: Summary?): String {
        callCount++
        lastPreviousSummary = previousSummary
        lastTurnCount = turns.size
        return "Summary of ${turns.size} turns"
    }
}

private class InMemorySummaryRepository : SummaryRepository {
    private val store = mutableListOf<Pair<AgentSessionId, Summary>>()

    override suspend fun save(sessionId: AgentSessionId, summary: Summary) {
        store.add(sessionId to summary)
    }

    override suspend fun getBySession(sessionId: AgentSessionId): List<Summary> =
        store.filter { it.first == sessionId }.map { it.second }
}

private class InMemoryContextManagementRepository : ContextManagementRepository {
    private val store = mutableMapOf<AgentSessionId, ContextManagementType>()

    override suspend fun save(sessionId: AgentSessionId, type: ContextManagementType) {
        store[sessionId] = type
    }

    override suspend fun getBySession(sessionId: AgentSessionId): ContextManagementType =
        store[sessionId] ?: ContextManagementType.None

    override suspend fun delete(sessionId: AgentSessionId) {
        store.remove(sessionId)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :modules:domain:context-manager:test`
Expected: FAIL — `DefaultContextManager` constructor doesn't match

- [ ] **Step 3: Update DefaultContextManager**

Replace the full content of `DefaultContextManager.kt`:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.context.CompressedContext
import com.ai.challenge.core.context.CompressionContext
import com.ai.challenge.core.context.CompressionDecision
import com.ai.challenge.core.context.ContextCompressor
import com.ai.challenge.core.context.ContextManagementRepository
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.turn.Turn

class DefaultContextManager(
    private val contextManagementRepository: ContextManagementRepository,
    private val strategyFactory: ContextStrategyFactory,
    private val compressor: ContextCompressor,
    private val summaryRepository: SummaryRepository,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext {
        val type = contextManagementRepository.getBySession(sessionId)
        val strategy = strategyFactory.create(type)

        val existingSummaries = summaryRepository.getBySession(sessionId)
        val lastSummary = existingSummaries.maxByOrNull { it.toTurnIndex }
        val decision = strategy.evaluate(CompressionContext(history, lastSummary))

        return when (decision) {
            is CompressionDecision.Skip -> when (lastSummary) {
                null -> noCompression(history, newMessage)
                else -> reuseExistingSummary(lastSummary, history, newMessage)
            }

            is CompressionDecision.Compress ->
                compress(sessionId, history, newMessage, decision.partitionPoint, lastSummary)
        }
    }

    private suspend fun compress(
        sessionId: AgentSessionId,
        history: List<Turn>,
        newMessage: String,
        splitAt: Int,
        lastSummary: Summary?,
    ): CompressedContext {
        val toRetain = history.subList(splitAt, history.size)

        val summaryText = when (lastSummary) {
            null -> compressor.compress(history.subList(0, splitAt))
            else -> compressor.compress(
                history.subList(lastSummary.toTurnIndex, splitAt),
                previousSummary = lastSummary,
            )
        }

        summaryRepository.save(
            sessionId,
            Summary(
                text = summaryText,
                fromTurnIndex = 0,
                toTurnIndex = splitAt,
            ),
        )

        return CompressedContext(
            messages = buildMessages(summaryText, toRetain, newMessage),
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = toRetain.size,
            summaryCount = 1,
        )
    }

    private fun noCompression(history: List<Turn>, newMessage: String): CompressedContext {
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

    private fun reuseExistingSummary(
        summary: Summary,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext {
        val turnsAfterSummary = history.subList(summary.toTurnIndex, history.size)
        return CompressedContext(
            messages = buildMessages(summary.text, turnsAfterSummary, newMessage),
            compressed = true,
            originalTurnCount = history.size,
            retainedTurnCount = turnsAfterSummary.size,
            summaryCount = 1,
        )
    }

    private fun buildMessages(
        summaryText: String,
        retainedTurns: List<Turn>,
        newMessage: String,
    ): List<ContextMessage> = buildList {
        add(ContextMessage(MessageRole.System, "Previous conversation summary:\n$summaryText"))
        for (turn in retainedTurns) {
            add(ContextMessage(MessageRole.User, turn.userMessage))
            add(ContextMessage(MessageRole.Assistant, turn.agentResponse))
        }
        add(ContextMessage(MessageRole.User, newMessage))
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :modules:domain:context-manager:test`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add modules/domain/context-manager/src/
git commit -m "refactor: update DefaultContextManager to use repository and factory"
```

---

### Task 7: Data — context-management-repository-exposed module

**Files:**
- Create: `modules/data/context-management-repository-exposed/build.gradle.kts`
- Create: `modules/data/context-management-repository-exposed/src/main/kotlin/com/ai/challenge/context/repository/ContextManagementTable.kt`
- Create: `modules/data/context-management-repository-exposed/src/main/kotlin/com/ai/challenge/context/repository/ExposedContextManagementRepository.kt`
- Create: `modules/data/context-management-repository-exposed/src/main/kotlin/com/ai/challenge/context/repository/DatabaseFactory.kt`
- Modify: `settings.gradle.kts` (add module include)

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
    implementation(project(":modules:core"))
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

- [ ] **Step 2: Create ContextManagementTable**

```kotlin
package com.ai.challenge.context.repository

import org.jetbrains.exposed.sql.Table

object ContextManagementTable : Table("context_management") {
    val sessionId = varchar("session_id", 36)
    val type = varchar("type", 50)

    override val primaryKey = PrimaryKey(sessionId)
}
```

- [ ] **Step 3: Create DatabaseFactory**

```kotlin
package com.ai.challenge.context.repository

import org.jetbrains.exposed.sql.Database
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

fun createContextManagementDatabase(): Database {
    val dbDir = Path(System.getProperty("user.home"), ".ai-challenge")
    dbDir.createDirectories()
    val dbPath = dbDir.resolve("context_management.db")
    return Database.connect(
        url = "jdbc:sqlite:$dbPath",
        driver = "org.sqlite.JDBC",
    )
}
```

- [ ] **Step 4: Create ExposedContextManagementRepository**

```kotlin
package com.ai.challenge.context.repository

import com.ai.challenge.core.context.ContextManagementRepository
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSessionId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

class ExposedContextManagementRepository(
    private val database: Database,
) : ContextManagementRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(ContextManagementTable)
        }
    }

    override suspend fun save(sessionId: AgentSessionId, type: ContextManagementType) {
        transaction(database) {
            ContextManagementTable.upsert {
                it[ContextManagementTable.sessionId] = sessionId.value
                it[ContextManagementTable.type] = type.toStorageString()
            }
        }
    }

    override suspend fun getBySession(sessionId: AgentSessionId): ContextManagementType =
        transaction(database) {
            ContextManagementTable.selectAll()
                .where { ContextManagementTable.sessionId eq sessionId.value }
                .singleOrNull()
                ?.let { it[ContextManagementTable.type].toContextManagementType() }
                ?: ContextManagementType.None
        }

    override suspend fun delete(sessionId: AgentSessionId) {
        transaction(database) {
            ContextManagementTable.deleteWhere { ContextManagementTable.sessionId eq sessionId.value }
        }
    }
}

private fun ContextManagementType.toStorageString(): String = when (this) {
    is ContextManagementType.None -> "none"
    is ContextManagementType.SummarizeOnThreshold -> "summarize_on_threshold"
}

private fun String.toContextManagementType(): ContextManagementType = when (this) {
    "none" -> ContextManagementType.None
    "summarize_on_threshold" -> ContextManagementType.SummarizeOnThreshold
    else -> ContextManagementType.None
}
```

- [ ] **Step 5: Add module to settings.gradle.kts**

Add after `include(":modules:data:summary-repository-exposed")`:

```
include(":modules:data:context-management-repository-exposed")
```

- [ ] **Step 6: Create test**

Create `modules/data/context-management-repository-exposed/src/test/kotlin/com/ai/challenge/context/repository/ExposedContextManagementRepositoryTest.kt`:

```kotlin
package com.ai.challenge.context.repository

import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSessionId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExposedContextManagementRepositoryTest {

    private lateinit var repository: ExposedContextManagementRepository

    @BeforeTest
    fun setup() {
        val database = Database.connect("jdbc:sqlite::memory:", driver = "org.sqlite.JDBC")
        repository = ExposedContextManagementRepository(database)
    }

    @Test
    fun `save and retrieve None type`() = runTest {
        val sessionId = AgentSessionId("s1")
        repository.save(sessionId, ContextManagementType.None)

        val result = repository.getBySession(sessionId)
        assertIs<ContextManagementType.None>(result)
    }

    @Test
    fun `save and retrieve SummarizeOnThreshold type`() = runTest {
        val sessionId = AgentSessionId("s1")
        repository.save(sessionId, ContextManagementType.SummarizeOnThreshold)

        val result = repository.getBySession(sessionId)
        assertIs<ContextManagementType.SummarizeOnThreshold>(result)
    }

    @Test
    fun `returns None for unknown session`() = runTest {
        val result = repository.getBySession(AgentSessionId("unknown"))
        assertIs<ContextManagementType.None>(result)
    }

    @Test
    fun `save overwrites existing type`() = runTest {
        val sessionId = AgentSessionId("s1")
        repository.save(sessionId, ContextManagementType.None)
        repository.save(sessionId, ContextManagementType.SummarizeOnThreshold)

        val result = repository.getBySession(sessionId)
        assertIs<ContextManagementType.SummarizeOnThreshold>(result)
    }

    @Test
    fun `delete removes entry`() = runTest {
        val sessionId = AgentSessionId("s1")
        repository.save(sessionId, ContextManagementType.SummarizeOnThreshold)
        repository.delete(sessionId)

        val result = repository.getBySession(sessionId)
        assertIs<ContextManagementType.None>(result)
    }

    @Test
    fun `different sessions have independent types`() = runTest {
        val s1 = AgentSessionId("s1")
        val s2 = AgentSessionId("s2")
        repository.save(s1, ContextManagementType.None)
        repository.save(s2, ContextManagementType.SummarizeOnThreshold)

        assertIs<ContextManagementType.None>(repository.getBySession(s1))
        assertIs<ContextManagementType.SummarizeOnThreshold>(repository.getBySession(s2))
    }
}
```

- [ ] **Step 7: Run tests**

Run: `./gradlew :modules:data:context-management-repository-exposed:test`
Expected: ALL PASS

- [ ] **Step 8: Commit**

```bash
git add modules/data/context-management-repository-exposed/ settings.gradle.kts
git commit -m "feat: add context-management-repository-exposed module"
```

---

### Task 8: Core — Add context management methods to Agent interface

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/agent/Agent.kt`

- [ ] **Step 1: Add new methods**

Add these two methods to the `Agent` interface:

```kotlin
    suspend fun getContextManagementType(sessionId: AgentSessionId): Either<AgentError, ContextManagementType>
    suspend fun updateContextManagementType(sessionId: AgentSessionId, type: ContextManagementType): Either<AgentError, Unit>
```

Add imports:

```kotlin
import com.ai.challenge.core.context.ContextManagementType
```

- [ ] **Step 2: Verify core compiles**

Run: `./gradlew :modules:core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/agent/Agent.kt
git commit -m "feat: add context management methods to Agent interface"
```

---

### Task 9: Domain — Implement Agent methods + session lifecycle in AiAgent

**Files:**
- Modify: `modules/domain/ai-agent/src/main/kotlin/com/ai/challenge/agent/AiAgent.kt`
- Modify: `modules/domain/ai-agent/build.gradle.kts`

- [ ] **Step 1: Add core dependency for ContextManagementRepository**

The `ai-agent` module already depends on `:modules:core`, which contains the interfaces. No build.gradle.kts change needed.

- [ ] **Step 2: Update AiAgent constructor and implement methods**

Add `contextManagementRepository` to constructor and implement new methods. Also update `createSession` and `deleteSession`:

```kotlin
package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.ai.challenge.core.agent.Agent
import com.ai.challenge.core.agent.AgentError
import com.ai.challenge.core.agent.AgentResponse
import com.ai.challenge.core.context.ContextManagementRepository
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.cost.CostDetails
import com.ai.challenge.core.cost.CostDetailsRepository
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.session.AgentSessionRepository
import com.ai.challenge.core.token.TokenDetails
import com.ai.challenge.core.token.TokenDetailsRepository
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.turn.TurnRepository
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.llm.model.ChatResponse

class AiAgent(
    private val service: OpenRouterService,
    private val model: String,
    private val sessionRepository: AgentSessionRepository,
    private val turnRepository: TurnRepository,
    private val tokenRepository: TokenDetailsRepository,
    private val costRepository: CostDetailsRepository,
    private val contextManager: ContextManager,
    private val contextManagementRepository: ContextManagementRepository,
) : Agent {

    override suspend fun send(sessionId: AgentSessionId, message: String): Either<AgentError, AgentResponse> = either {
        val history = turnRepository.getBySession(sessionId)

        val context = catch({
            contextManager.prepareContext(sessionId, history, message)
        }) { e: Exception ->
            raise(AgentError.NetworkError(e.message ?: "Context preparation failed"))
        }

        val chatResponse = catch({
            service.chat(model = model) {
                for (msg in context.messages) {
                    message(msg.role.toApiRole(), msg.content)
                }
            }
        }) { e: Exception ->
            val msg = e.message ?: "Unknown error"
            if (msg.startsWith("OpenRouter API error:")) {
                raise(AgentError.ApiError(msg.removePrefix("OpenRouter API error: ")))
            } else {
                raise(AgentError.NetworkError(msg))
            }
        }

        val error = chatResponse.error
        if (error != null) {
            raise(AgentError.ApiError(error.message ?: "Unknown API error"))
        }

        val text = chatResponse.choices.firstOrNull()?.message?.content
            ?: raise(AgentError.ApiError("Empty response from OpenRouter"))

        val tokenDetails = chatResponse.toTokenDetails()
        val costDetails = chatResponse.toCostDetails()
        val turn = Turn(userMessage = message, agentResponse = text)
        val turnId = turnRepository.append(sessionId, turn)
        tokenRepository.record(sessionId, turnId, tokenDetails)
        costRepository.record(sessionId, turnId, costDetails)

        AgentResponse(text = text, turnId = turnId, tokenDetails = tokenDetails, costDetails = costDetails)
    }

    override suspend fun createSession(title: String): AgentSessionId {
        val sessionId = sessionRepository.create(title)
        contextManagementRepository.save(sessionId, ContextManagementType.None)
        return sessionId
    }

    override suspend fun deleteSession(id: AgentSessionId): Boolean {
        contextManagementRepository.delete(id)
        return sessionRepository.delete(id)
    }

    override suspend fun listSessions(): List<AgentSession> = sessionRepository.list()
    override suspend fun getSession(id: AgentSessionId): AgentSession? = sessionRepository.get(id)
    override suspend fun updateSessionTitle(id: AgentSessionId, title: String) = sessionRepository.updateTitle(id, title)
    override suspend fun getTurns(sessionId: AgentSessionId, limit: Int?): List<Turn> = turnRepository.getBySession(sessionId, limit)
    override suspend fun getTokensByTurn(turnId: TurnId): TokenDetails? = tokenRepository.getByTurn(turnId)
    override suspend fun getTokensBySession(sessionId: AgentSessionId): Map<TurnId, TokenDetails> = tokenRepository.getBySession(sessionId)
    override suspend fun getSessionTotalTokens(sessionId: AgentSessionId): TokenDetails = tokenRepository.getSessionTotal(sessionId)
    override suspend fun getCostByTurn(turnId: TurnId): CostDetails? = costRepository.getByTurn(turnId)
    override suspend fun getCostBySession(sessionId: AgentSessionId): Map<TurnId, CostDetails> = costRepository.getBySession(sessionId)
    override suspend fun getSessionTotalCost(sessionId: AgentSessionId): CostDetails = costRepository.getSessionTotal(sessionId)

    override suspend fun getContextManagementType(sessionId: AgentSessionId): Either<AgentError, ContextManagementType> =
        Either.Right(contextManagementRepository.getBySession(sessionId))

    override suspend fun updateContextManagementType(
        sessionId: AgentSessionId,
        type: ContextManagementType,
    ): Either<AgentError, Unit> {
        contextManagementRepository.save(sessionId, type)
        return Either.Right(Unit)
    }
}

fun MessageRole.toApiRole(): String = when (this) {
    MessageRole.System -> "system"
    MessageRole.User -> "user"
    MessageRole.Assistant -> "assistant"
}

private fun ChatResponse.toTokenDetails(): TokenDetails = TokenDetails(
    promptTokens = usage?.promptTokens ?: 0,
    completionTokens = usage?.completionTokens ?: 0,
    cachedTokens = usage?.promptTokensDetails?.cachedTokens ?: 0,
    cacheWriteTokens = usage?.promptTokensDetails?.cacheWriteTokens ?: 0,
    reasoningTokens = usage?.completionTokensDetails?.reasoningTokens ?: 0,
)

private fun ChatResponse.toCostDetails(): CostDetails = CostDetails(
    totalCost = usage?.cost ?: cost ?: 0.0,
    upstreamCost = usage?.costDetails?.upstreamCost ?: costDetails?.upstreamCost ?: 0.0,
    upstreamPromptCost = usage?.costDetails?.upstreamPromptCost ?: costDetails?.upstreamPromptCost ?: 0.0,
    upstreamCompletionsCost = usage?.costDetails?.upstreamCompletionsCost ?: costDetails?.upstreamCompletionsCost ?: 0.0,
)
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :modules:domain:ai-agent:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add modules/domain/ai-agent/src/
git commit -m "feat: implement context management methods in AiAgent"
```

---

### Task 10: DI — Update AppModule

**Files:**
- Modify: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt`
- Modify: `modules/presentation/app/build.gradle.kts`

- [ ] **Step 1: Add context-management-repository-exposed to app build.gradle.kts**

Add after `implementation(project(":modules:data:summary-repository-exposed"))`:

```kotlin
    implementation(project(":modules:data:context-management-repository-exposed"))
```

- [ ] **Step 2: Update AppModule**

Replace full content of `AppModule.kt`:

```kotlin
package com.ai.challenge.app.di

import com.ai.challenge.agent.AiAgent
import com.ai.challenge.compressor.LlmContextCompressor
import com.ai.challenge.context.ContextStrategyFactory
import com.ai.challenge.context.DefaultContextManager
import com.ai.challenge.context.repository.ExposedContextManagementRepository
import com.ai.challenge.context.repository.createContextManagementDatabase
import com.ai.challenge.core.agent.Agent
import com.ai.challenge.core.context.ContextCompressor
import com.ai.challenge.core.context.ContextManagementRepository
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.cost.CostDetailsRepository
import com.ai.challenge.core.session.AgentSessionRepository
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.token.TokenDetailsRepository
import com.ai.challenge.core.turn.TurnRepository
import com.ai.challenge.cost.repository.ExposedCostRepository
import com.ai.challenge.cost.repository.createCostDatabase
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.session.repository.ExposedSessionRepository
import com.ai.challenge.session.repository.createSessionDatabase
import com.ai.challenge.summary.repository.ExposedSummaryRepository
import com.ai.challenge.summary.repository.createSummaryDatabase
import com.ai.challenge.token.repository.ExposedTokenRepository
import com.ai.challenge.token.repository.createTokenDatabase
import com.ai.challenge.turn.repository.ExposedTurnRepository
import com.ai.challenge.turn.repository.createTurnDatabase
import org.koin.dsl.module

val appModule = module {
    single {
        OpenRouterService(
            apiKey = System.getenv("OPENROUTER_API_KEY")
                ?: error("OPENROUTER_API_KEY environment variable is not set"),
        )
    }
    single<AgentSessionRepository> { ExposedSessionRepository(createSessionDatabase()) }
    single<TurnRepository> { ExposedTurnRepository(createTurnDatabase()) }
    single<TokenDetailsRepository> { ExposedTokenRepository(createTokenDatabase()) }
    single<CostDetailsRepository> { ExposedCostRepository(createCostDatabase()) }
    single<SummaryRepository> { ExposedSummaryRepository(createSummaryDatabase()) }
    single<ContextManagementRepository> { ExposedContextManagementRepository(createContextManagementDatabase()) }
    single { ContextStrategyFactory() }
    single<ContextCompressor> { LlmContextCompressor(service = get(), model = "google/gemini-2.0-flash-001") }
    single<ContextManager> {
        DefaultContextManager(
            contextManagementRepository = get(),
            strategyFactory = get(),
            compressor = get(),
            summaryRepository = get(),
        )
    }
    single<Agent> {
        AiAgent(
            service = get(),
            model = "google/gemini-2.0-flash-001",
            sessionRepository = get(),
            turnRepository = get(),
            tokenRepository = get(),
            costRepository = get(),
            contextManager = get(),
            contextManagementRepository = get(),
        )
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :modules:presentation:app:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add modules/presentation/app/build.gradle.kts modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt
git commit -m "feat: wire ContextManagementRepository and factory in DI"
```

---

### Task 11: UI — SessionSettingsStore

**Files:**
- Create: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/settings/store/SessionSettingsStore.kt`
- Create: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/settings/store/SessionSettingsStoreFactory.kt`

- [ ] **Step 1: Create SessionSettingsStore interface**

```kotlin
package com.ai.challenge.ui.settings.store

import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSessionId
import com.arkivanov.mvikotlin.core.store.Store

interface SessionSettingsStore : Store<SessionSettingsStore.Intent, SessionSettingsStore.State, Nothing> {

    sealed interface Intent {
        data class LoadSettings(val sessionId: AgentSessionId) : Intent
        data class ChangeContextManagementType(val type: ContextManagementType) : Intent
    }

    data class State(
        val sessionId: AgentSessionId?,
        val currentType: ContextManagementType,
        val isLoading: Boolean,
    )
}
```

- [ ] **Step 2: Create SessionSettingsStoreFactory**

```kotlin
package com.ai.challenge.ui.settings.store

import arrow.core.Either
import com.ai.challenge.core.agent.Agent
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSessionId
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class SessionSettingsStoreFactory(
    private val storeFactory: StoreFactory,
    private val agent: Agent,
) {
    fun create(): SessionSettingsStore =
        object : SessionSettingsStore,
            Store<SessionSettingsStore.Intent, SessionSettingsStore.State, Nothing> by storeFactory.create(
                name = "SessionSettingsStore",
                initialState = SessionSettingsStore.State(
                    sessionId = null,
                    currentType = ContextManagementType.None,
                    isLoading = false,
                ),
                executorFactory = { ExecutorImpl(agent) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class SettingsLoaded(
            val sessionId: AgentSessionId,
            val type: ContextManagementType,
        ) : Msg
        data class TypeChanged(val type: ContextManagementType) : Msg
        data object Loading : Msg
        data object LoadingComplete : Msg
    }

    private class ExecutorImpl(
        private val agent: Agent,
    ) : CoroutineExecutor<SessionSettingsStore.Intent, Nothing, SessionSettingsStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: SessionSettingsStore.Intent) {
            when (intent) {
                is SessionSettingsStore.Intent.LoadSettings -> handleLoadSettings(intent.sessionId)
                is SessionSettingsStore.Intent.ChangeContextManagementType -> handleChangeType(intent.type)
            }
        }

        private fun handleLoadSettings(sessionId: AgentSessionId) {
            dispatch(Msg.Loading)
            scope.launch {
                when (val result = agent.getContextManagementType(sessionId)) {
                    is Either.Right -> dispatch(Msg.SettingsLoaded(sessionId, result.value))
                    is Either.Left -> dispatch(Msg.SettingsLoaded(sessionId, ContextManagementType.None))
                }
                dispatch(Msg.LoadingComplete)
            }
        }

        private fun handleChangeType(type: ContextManagementType) {
            val sessionId = state().sessionId ?: return
            dispatch(Msg.Loading)
            scope.launch {
                when (agent.updateContextManagementType(sessionId, type)) {
                    is Either.Right -> dispatch(Msg.TypeChanged(type))
                    is Either.Left -> {}
                }
                dispatch(Msg.LoadingComplete)
            }
        }
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<SessionSettingsStore.State, Msg> {
        override fun SessionSettingsStore.State.reduce(msg: Msg): SessionSettingsStore.State =
            when (msg) {
                is Msg.SettingsLoaded -> copy(sessionId = msg.sessionId, currentType = msg.type)
                is Msg.TypeChanged -> copy(currentType = msg.type)
                is Msg.Loading -> copy(isLoading = true)
                is Msg.LoadingComplete -> copy(isLoading = false)
            }
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :modules:presentation:compose-ui:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/settings/
git commit -m "feat: add SessionSettingsStore and factory"
```

---

### Task 12: UI — SessionSettingsComponent

**Files:**
- Create: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/settings/SessionSettingsComponent.kt`

- [ ] **Step 1: Create component**

```kotlin
package com.ai.challenge.ui.settings

import com.ai.challenge.core.agent.Agent
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.ui.settings.store.SessionSettingsStore
import com.ai.challenge.ui.settings.store.SessionSettingsStoreFactory
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow

class SessionSettingsComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    agent: Agent,
    sessionId: AgentSessionId,
) : ComponentContext by componentContext {

    private val store = instanceKeeper.getStore {
        SessionSettingsStoreFactory(storeFactory, agent).create()
    }

    init {
        store.accept(SessionSettingsStore.Intent.LoadSettings(sessionId))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SessionSettingsStore.State> = store.stateFlow

    fun onChangeType(type: ContextManagementType) {
        store.accept(SessionSettingsStore.Intent.ChangeContextManagementType(type))
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :modules:presentation:compose-ui:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/settings/SessionSettingsComponent.kt
git commit -m "feat: add SessionSettingsComponent"
```

---

### Task 13: UI — SessionSettingsContent dialog composable

**Files:**
- Create: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/settings/SessionSettingsContent.kt`

- [ ] **Step 1: Create dialog composable**

```kotlin
package com.ai.challenge.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.challenge.core.context.ContextManagementType

@Composable
fun SessionSettingsDialog(
    component: SessionSettingsComponent,
    onDismiss: () -> Unit,
) {
    val state by component.state.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp).width(320.dp),
            ) {
                Text(
                    text = "Session Settings",
                    style = MaterialTheme.typography.titleLarge,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Context Management",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    ContextManagementTypeOption(
                        label = "No management",
                        description = "Full history sent as-is",
                        selected = state.currentType is ContextManagementType.None,
                        onClick = { component.onChangeType(ContextManagementType.None) },
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    ContextManagementTypeOption(
                        label = "Summarize on threshold",
                        description = "Compress via summary when history grows",
                        selected = state.currentType is ContextManagementType.SummarizeOnThreshold,
                        onClick = { component.onChangeType(ContextManagementType.SummarizeOnThreshold) },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextManagementTypeOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :modules:presentation:compose-ui:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/settings/SessionSettingsContent.kt
git commit -m "feat: add SessionSettingsDialog composable"
```

---

### Task 14: UI — Wire settings dialog into RootComponent and RootContent

**Files:**
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootComponent.kt`
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootContent.kt`

- [ ] **Step 1: Add settings dialog state to RootComponent**

Add to `RootComponent`:

1. Add a `MutableStateFlow` for the settings component:

```kotlin
import com.ai.challenge.ui.settings.SessionSettingsComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
```

2. Add field after `sessionListState`:

```kotlin
    private val _settingsComponent = MutableStateFlow<SessionSettingsComponent?>(null)
    val settingsComponent: StateFlow<SessionSettingsComponent?> = _settingsComponent.asStateFlow()
```

3. Add methods:

```kotlin
    fun openSessionSettings(sessionId: AgentSessionId) {
        _settingsComponent.value = SessionSettingsComponent(
            componentContext = this,
            storeFactory = storeFactory,
            agent = agent,
            sessionId = sessionId,
        )
    }

    fun closeSessionSettings() {
        _settingsComponent.value = null
    }
```

- [ ] **Step 2: Update RootContent to show the dialog**

In `RootContent.kt`:

1. Add imports:

```kotlin
import com.ai.challenge.ui.settings.SessionSettingsDialog
import androidx.compose.material.icons.filled.Settings
```

2. After `val sessionListState by component.sessionListState.collectAsState()`, add:

```kotlin
    val settingsComponent by component.settingsComponent.collectAsState()
```

3. Add the dialog at the end of `RootContent`, just before the closing `}`:

```kotlin
    settingsComponent?.let { settings ->
        SessionSettingsDialog(
            component = settings,
            onDismiss = { component.closeSessionSettings() },
        )
    }
```

4. Add a gear icon button in the header bar, after the "AI Chat" Text:

```kotlin
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        sessionListState.activeSessionId?.let { component.openSessionSettings(it) }
                    },
                    enabled = sessionListState.activeSessionId != null,
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Session settings")
                }
```

5. Update `DrawerContent` signature to accept `onOpenSettings`:

```kotlin
@Composable
private fun DrawerContent(
    state: SessionListStore.State,
    onNewChat: () -> Unit,
    onSelectSession: (AgentSessionId) -> Unit,
    onDeleteSession: (AgentSessionId) -> Unit,
    onOpenSettings: (AgentSessionId) -> Unit,
)
```

6. Update `DrawerContent` call in `ModalNavigationDrawer`:

```kotlin
            DrawerContent(
                state = sessionListState,
                onNewChat = {
                    component.createNewSession()
                    scope.launch { drawerState.close() }
                },
                onSelectSession = { sessionId ->
                    component.selectSession(sessionId)
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = { sessionId ->
                    component.deleteSession(sessionId)
                },
                onOpenSettings = { sessionId ->
                    component.openSessionSettings(sessionId)
                    scope.launch { drawerState.close() }
                },
            )
```

7. Update `SessionRow` to accept and show settings icon:

```kotlin
@Composable
private fun SessionRow(
    session: SessionListStore.SessionItem,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit,
)
```

Add a settings icon button in `SessionRow`, before the delete button:

```kotlin
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Session settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
```

8. Update `SessionRow` call site in `DrawerContent` LazyColumn:

```kotlin
                    SessionRow(
                        session = session,
                        isActive = session.id == state.activeSessionId,
                        onSelect = { onSelectSession(session.id) },
                        onDelete = { onDeleteSession(session.id) },
                        onOpenSettings = { onOpenSettings(session.id) },
                    )
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :modules:presentation:compose-ui:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootComponent.kt modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootContent.kt
git commit -m "feat: wire session settings dialog into root UI"
```

---

### Task 15: Verify full build and run

**Files:** None (verification only)

- [ ] **Step 1: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 2: Verify app starts**

Run: `OPENROUTER_API_KEY=test ./gradlew :modules:presentation:app:run`
Expected: App window opens (will fail on API call but UI should render)

- [ ] **Step 3: Final commit if any fixes needed**

If fixes were made, commit them.
