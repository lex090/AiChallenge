# Sliding Window Context Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `SlidingWindow` context management strategy that keeps only the last 10 turns, discarding older history without summarization.

**Architecture:** New `ContextManagementType.SlidingWindow` data object in core, with routing and logic added to `DefaultContextManager`. Serialization support in exposed repository, UI option in settings panel.

**Tech Stack:** Kotlin, kotlin-test, kotlinx-coroutines-test

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementType.kt` | Add `SlidingWindow` variant |
| Modify | `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt` | Add `WINDOW_SIZE` constant, `slidingWindow()` method, routing branch |
| Modify | `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt` | Add 3 test cases |
| Modify | `modules/data/context-management-repository-exposed/src/main/kotlin/com/ai/challenge/context/repository/ExposedContextManagementTypeRepository.kt` | Add serialization branches |
| Modify | `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/settings/SessionSettingsContent.kt` | Add radio button option |

---

### Task 1: Add `SlidingWindow` to `ContextManagementType`

**Files:**
- Modify: `modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementType.kt`

- [ ] **Step 1: Add the new variant**

Replace the entire file content with:

```kotlin
package com.ai.challenge.core.context

sealed interface ContextManagementType {
    data object None : ContextManagementType
    data object SummarizeOnThreshold : ContextManagementType
    data object SlidingWindow : ContextManagementType
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :modules:core:compileKotlin`

Expected: BUILD SUCCESSFUL (the new variant is added but not yet referenced — sealed `when` expressions in other modules will now require updating, which is expected and handled in subsequent tasks)

- [ ] **Step 3: Commit**

```bash
git add modules/core/src/main/kotlin/com/ai/challenge/core/context/ContextManagementType.kt
git commit -m "feat(core): add SlidingWindow variant to ContextManagementType"
```

---

### Task 2: Write failing tests for sliding window in `DefaultContextManagerTest`

**Files:**
- Modify: `modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt`

- [ ] **Step 1: Add test for history smaller than window**

Add this test method inside `DefaultContextManagerTest` class, after the existing `handles empty history` test:

```kotlin
@Test
fun `sliding window returns all turns when history is smaller than window`() = runTest {
    val sessionId = AgentSessionId("s1")
    fakeContextManagementRepo.save(sessionId, ContextManagementType.SlidingWindow)
    saveTurns(sessionId, turns(5))
    val manager = createManager()

    val result = manager.prepareContext(sessionId, "new msg")

    assertFalse(result.compressed)
    assertEquals(5, result.originalTurnCount)
    assertEquals(5, result.retainedTurnCount)
    assertEquals(0, result.summaryCount)
    assertEquals(11, result.messages.size)
    assertEquals(ContextMessage(MessageRole.User, "new msg"), result.messages.last())
}
```

- [ ] **Step 2: Add test for history larger than window**

Add this test method right after the previous one:

```kotlin
@Test
fun `sliding window retains only last 10 turns when history exceeds window`() = runTest {
    val sessionId = AgentSessionId("s1")
    fakeContextManagementRepo.save(sessionId, ContextManagementType.SlidingWindow)
    saveTurns(sessionId, turns(15))
    val manager = createManager()

    val result = manager.prepareContext(sessionId, "new msg")

    assertFalse(result.compressed)
    assertEquals(15, result.originalTurnCount)
    assertEquals(10, result.retainedTurnCount)
    assertEquals(0, result.summaryCount)
    assertEquals(21, result.messages.size)
    assertEquals(ContextMessage(MessageRole.User, "msg6"), result.messages.first().content.let { result.messages[0] }.let {
        assertEquals(MessageRole.User, it.role)
        assertEquals("msg6", it.content)
        it
    }.let { result.messages[0] })
}
```

Actually, let me write a cleaner version. Replace the above with:

```kotlin
@Test
fun `sliding window retains only last 10 turns when history exceeds window`() = runTest {
    val sessionId = AgentSessionId("s1")
    fakeContextManagementRepo.save(sessionId, ContextManagementType.SlidingWindow)
    saveTurns(sessionId, turns(15))
    val manager = createManager()

    val result = manager.prepareContext(sessionId, "new msg")

    assertFalse(result.compressed)
    assertEquals(15, result.originalTurnCount)
    assertEquals(10, result.retainedTurnCount)
    assertEquals(0, result.summaryCount)
    assertEquals(21, result.messages.size)
    assertEquals(MessageRole.User, result.messages.first().role)
    assertEquals("msg6", result.messages.first().content)
    assertEquals(ContextMessage(MessageRole.User, "new msg"), result.messages.last())
}
```

- [ ] **Step 3: Add test for empty history**

Add this test method right after the previous one:

```kotlin
@Test
fun `sliding window handles empty history`() = runTest {
    val sessionId = AgentSessionId("s1")
    fakeContextManagementRepo.save(sessionId, ContextManagementType.SlidingWindow)
    val manager = createManager()

    val result = manager.prepareContext(sessionId, "hello")

    assertFalse(result.compressed)
    assertEquals(0, result.originalTurnCount)
    assertEquals(0, result.retainedTurnCount)
    assertEquals(0, result.summaryCount)
    assertEquals(1, result.messages.size)
    assertEquals(ContextMessage(MessageRole.User, "hello"), result.messages[0])
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew :modules:domain:context-manager:test`

Expected: FAIL — compilation error because `DefaultContextManager.prepareContext` has a non-exhaustive `when` expression (missing `SlidingWindow` branch).

- [ ] **Step 5: Commit failing tests**

```bash
git add modules/domain/context-manager/src/test/kotlin/com/ai/challenge/context/DefaultContextManagerTest.kt
git commit -m "test(context-manager): add failing tests for sliding window strategy"
```

---

### Task 3: Implement sliding window logic in `DefaultContextManager`

**Files:**
- Modify: `modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt`

- [ ] **Step 1: Add the `WINDOW_SIZE` constant**

Add after the class declaration (line 20), before `prepareContext`:

```kotlin
class DefaultContextManager(
    private val contextManagementRepository: ContextManagementTypeRepository,
    private val compressor: ContextCompressor,
    private val summaryRepository: SummaryRepository,
    private val turnRepository: TurnRepository,
) : ContextManager {

    private companion object {
        const val WINDOW_SIZE = 10
    }
```

This replaces the existing class header (lines 15-20). The rest remains unchanged.

- [ ] **Step 2: Add the `SlidingWindow` branch to `when` expression**

In `prepareContext`, change the `when` block from:

```kotlin
return when (type) {
    is ContextManagementType.None -> passThrough(sessionId = sessionId, newMessage = newMessage)
    is ContextManagementType.SummarizeOnThreshold -> summarizeOnThreshold(
        sessionId = sessionId,
        newMessage = newMessage
    )
}
```

to:

```kotlin
return when (type) {
    is ContextManagementType.None -> passThrough(sessionId = sessionId, newMessage = newMessage)
    is ContextManagementType.SummarizeOnThreshold -> summarizeOnThreshold(
        sessionId = sessionId,
        newMessage = newMessage,
    )
    is ContextManagementType.SlidingWindow -> slidingWindow(
        sessionId = sessionId,
        newMessage = newMessage,
    )
}
```

- [ ] **Step 3: Add the `slidingWindow` method**

Add after the `passThrough` method (after line 45), in the orchestration section:

```kotlin
private suspend fun slidingWindow(
    sessionId: AgentSessionId,
    newMessage: String,
): PreparedContext {
    val history = turnRepository.getBySession(sessionId = sessionId)
    val windowed = history.takeLast(n = WINDOW_SIZE)
    return PreparedContext(
        messages = turnsToMessages(turns = windowed) + ContextMessage(role = MessageRole.User, content = newMessage),
        compressed = false,
        originalTurnCount = history.size,
        retainedTurnCount = windowed.size,
        summaryCount = 0,
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :modules:domain:context-manager:test`

Expected: BUILD SUCCESSFUL — all tests pass, including the 3 new sliding window tests.

- [ ] **Step 5: Commit**

```bash
git add modules/domain/context-manager/src/main/kotlin/com/ai/challenge/context/DefaultContextManager.kt
git commit -m "feat(context-manager): implement sliding window strategy"
```

---

### Task 4: Add serialization support in `ExposedContextManagementTypeRepository`

**Files:**
- Modify: `modules/data/context-management-repository-exposed/src/main/kotlin/com/ai/challenge/context/repository/ExposedContextManagementTypeRepository.kt`

- [ ] **Step 1: Add `SlidingWindow` to `toStorageString()`**

Change from:

```kotlin
private fun ContextManagementType.toStorageString(): String = when (this) {
    is ContextManagementType.None -> "none"
    is ContextManagementType.SummarizeOnThreshold -> "summarize_on_threshold"
}
```

to:

```kotlin
private fun ContextManagementType.toStorageString(): String = when (this) {
    is ContextManagementType.None -> "none"
    is ContextManagementType.SummarizeOnThreshold -> "summarize_on_threshold"
    is ContextManagementType.SlidingWindow -> "sliding_window"
}
```

- [ ] **Step 2: Add `SlidingWindow` to `toContextManagementType()`**

Change from:

```kotlin
private fun String.toContextManagementType(): ContextManagementType = when (this) {
    "none" -> ContextManagementType.None
    "summarize_on_threshold" -> ContextManagementType.SummarizeOnThreshold
    else -> ContextManagementType.None
}
```

to:

```kotlin
private fun String.toContextManagementType(): ContextManagementType = when (this) {
    "none" -> ContextManagementType.None
    "summarize_on_threshold" -> ContextManagementType.SummarizeOnThreshold
    "sliding_window" -> ContextManagementType.SlidingWindow
    else -> ContextManagementType.None
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :modules:data:context-management-repository-exposed:compileKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add modules/data/context-management-repository-exposed/src/main/kotlin/com/ai/challenge/context/repository/ExposedContextManagementTypeRepository.kt
git commit -m "feat(data): add sliding window serialization support"
```

---

### Task 5: Add UI option in `SessionSettingsContent`

**Files:**
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/settings/SessionSettingsContent.kt`

- [ ] **Step 1: Add the Sliding Window radio button**

After the existing `Summarize on threshold` option block (after line 108), add:

```kotlin
                Spacer(modifier = Modifier.height(4.dp))

                ContextManagementTypeOption(
                    label = "Sliding window",
                    description = "Keep last 10 turns, discard older",
                    selected = state.currentType is ContextManagementType.SlidingWindow,
                    onClick = { component.onChangeType(type = ContextManagementType.SlidingWindow) },
                )
```

Ensure the import for `ContextManagementType` is already present (it should be, since it's used by the existing options).

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :modules:presentation:compose-ui:compileKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/settings/SessionSettingsContent.kt
git commit -m "feat(ui): add sliding window option to session settings"
```

---

### Task 6: Full build verification

- [ ] **Step 1: Run full build and all tests**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL — all modules compile, all tests pass.

- [ ] **Step 2: Commit (if any adjustments were needed)**

If no adjustments needed, skip this step. Otherwise commit fixes.
