# Remove ContextStrategy/CompressionDecision Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the redundant `ContextStrategy`, `CompressionDecision`, and `CompressionContext` abstractions, inlining their logic directly into `DefaultContextManager`.

**Architecture:** `DefaultContextManager.prepareContext()` will use a `when(type)` on `ContextManagementType` to decide whether to compress, replacing the strategy pattern. The `ContextStrategyFactory`, `SummarizeOnThresholdStrategy`, and all core abstractions they depend on get deleted.

**Tech Stack:** Kotlin, kotlinx-coroutines-test, Koin

---

### File Map

| Action | File |
|--------|------|
| Modify | `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt` |
| Modify | `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt` |
| Modify | `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt` |
| Delete | `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextStrategy.kt` |
| Delete | `modules/core/src/main/kotlin/com/ai/challenge/core/context/CompressionContext.kt` |
| Delete | `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/ContextStrategyFactory.kt` |
| Delete | `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/SummarizeOnThresholdStrategy.kt` |
| Delete | `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/ContextStrategyFactoryTest.kt` |
| Delete | `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/SummarizeOnThresholdStrategyTest.kt` |

---

### Task 1: Update DefaultContextManager — inline strategy logic

**Files:**
- Modify: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt`

- [ ] **Step 1: Rewrite `DefaultContextManager` to inline decision logic**

Replace the entire file with:

```kotlin
package com.ai.challenge.context

import com.ai.challenge.core.context.CompressedContext
import com.ai.challenge.core.context.ContextCompressor
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextManagementTypeRepository
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.turn.Turn

class DefaultContextManager(
    private val contextManagementRepository: ContextManagementTypeRepository,
    private val compressor: ContextCompressor,
    private val summaryRepository: SummaryRepository,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        history: List<Turn>,
        newMessage: String,
    ): CompressedContext {
        val type = contextManagementRepository.getBySession(sessionId)
        val existingSummaries = summaryRepository.getBySession(sessionId)
        val lastSummary = existingSummaries.maxByOrNull { it.toTurnIndex }

        return when (type) {
            is ContextManagementType.None -> when (lastSummary) {
                null -> noCompression(history, newMessage)
                else -> reuseExistingSummary(lastSummary, history, newMessage)
            }

            is ContextManagementType.SummarizeOnThreshold -> {
                val maxTurns = 15
                val retainLast = 5
                val compressionInterval = 10

                val shouldCompress = when (val lastIndex = lastSummary?.toTurnIndex) {
                    null -> history.size >= maxTurns
                    else -> history.size - lastIndex >= retainLast + compressionInterval
                }

                if (!shouldCompress) {
                    when (lastSummary) {
                        null -> noCompression(history, newMessage)
                        else -> reuseExistingSummary(lastSummary, history, newMessage)
                    }
                } else {
                    val partitionPoint = (history.size - retainLast).coerceAtLeast(0)
                    compress(sessionId, history, newMessage, partitionPoint, lastSummary)
                }
            }
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

- [ ] **Step 2: Verify the file compiles**

Run: `./gradlew :modules:domain:context-manager:compileKotlin`
Expected: BUILD SUCCESSFUL (may have warnings about unused imports in test file — that's fine, we fix tests next)

---

### Task 2: Update DefaultContextManagerTest

**Files:**
- Modify: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt`

- [ ] **Step 1: Update `createManager()` to remove `strategyFactory` parameter**

In `DefaultContextManagerTest.kt`, replace the `createManager()` method:

```kotlin
private fun createManager(): DefaultContextManager =
    DefaultContextManager(
        contextManagementRepository = fakeContextManagementRepo,
        compressor = fakeCompressor,
        summaryRepository = fakeSummaryRepo,
    )
```

Also remove this import if present:
```
import com.ai.challenge.core.context.CompressionContext
```

- [ ] **Step 2: Run tests to verify they all pass**

Run: `./gradlew :modules:domain:context-manager:test`
Expected: 5 tests pass (the existing `DefaultContextManagerTest` tests). `ContextStrategyFactoryTest` and `SummarizeOnThresholdStrategyTest` will fail to compile — that's expected, we delete them next.

---

### Task 3: Delete strategy-related files and tests

**Files:**
- Delete: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/ContextStrategyFactory.kt`
- Delete: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/SummarizeOnThresholdStrategy.kt`
- Delete: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/ContextStrategyFactoryTest.kt`
- Delete: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/SummarizeOnThresholdStrategyTest.kt`
- Delete: `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextStrategy.kt`
- Delete: `modules/core/src/main/kotlin/com/ai/challenge/core/context/CompressionContext.kt`

- [ ] **Step 1: Delete the four context-manager files**

```bash
rm modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/ContextStrategyFactory.kt
rm modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/SummarizeOnThresholdStrategy.kt
rm modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/ContextStrategyFactoryTest.kt
rm modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/SummarizeOnThresholdStrategyTest.kt
```

- [ ] **Step 2: Delete the two core files**

```bash
rm modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextStrategy.kt
rm modules/core/src/main/kotlin/com/ai/challenge/core/context/CompressionContext.kt
```

- [ ] **Step 3: Run context-manager tests**

Run: `./gradlew :modules:domain:context-manager:test`
Expected: 5 tests pass, BUILD SUCCESSFUL

---

### Task 4: Update AppModule DI wiring

**Files:**
- Modify: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/di/AppModule.kt`

- [ ] **Step 1: Remove ContextStrategyFactory from DI**

Remove this import:
```kotlin
import com.ai.challenge.context.ContextStrategyFactory
```

Remove this binding:
```kotlin
single { ContextStrategyFactory() }
```

Update the `DefaultContextManager` binding — remove the `strategyFactory = get()` parameter:
```kotlin
single<ContextManager> {
    DefaultContextManager(
        contextManagementRepository = get(),
        compressor = get(),
        summaryRepository = get(),
    )
}
```

- [ ] **Step 2: Run full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 3: Commit all changes**

```bash
git add -A
git commit -m "refactor: remove ContextStrategy/CompressionDecision, inline logic into DefaultContextManager"
```
