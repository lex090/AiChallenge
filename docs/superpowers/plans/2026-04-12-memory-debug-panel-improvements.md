# Memory Debug Panel Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enhance the Memory Debug panel with context-aware display, Facts CRUD (editable table with per-row save/delete, add, category filter), and read-only Summaries.

**Architecture:** All changes in presentation layer. Update MemoryDebugStore (new State fields, new Intents), rewrite MemoryDebugStoreFactory (new Executor/Reducer logic, replace summary use cases with SessionService), rebuild MemoryDebugScreen composable (editable table, filter chips, context-aware sections), update Main.kt DI wiring.

**Tech Stack:** Kotlin, Compose Multiplatform (Material3), MVIKotlin, Decompose, Arrow Either

---

### Task 1: Update MemoryDebugStore — new State and Intents

**Files:**
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/debug/memory/MemoryDebugStore.kt`

- [ ] **Step 1: Replace the full file content**

```kotlin
package com.ai.challenge.ui.debug.memory

import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.arkivanov.mvikotlin.core.store.Store

/**
 * MVIKotlin Store contract for the Memory Debug Panel.
 *
 * Exposes intents to load memory, manage facts (CRUD), filter by category,
 * and a state with context-aware display logic.
 */
interface MemoryDebugStore : Store<MemoryDebugStore.Intent, MemoryDebugStore.State, Nothing> {

    sealed interface Intent {
        data class LoadMemory(val sessionId: AgentSessionId) : Intent
        data class SaveFact(val index: Int, val fact: Fact) : Intent
        data class DeleteFact(val index: Int) : Intent
        data object AddFact : Intent
        data class ToggleCategory(val category: FactCategory) : Intent
        data object SelectAllCategories : Intent
    }

    data class State(
        val sessionId: AgentSessionId?,
        val contextManagementType: ContextManagementType?,
        val facts: List<Fact>,
        val summaries: List<Summary>,
        val selectedCategories: Set<FactCategory>,
        val isLoading: Boolean,
        val error: String?,
    )
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :modules:presentation:compose-ui:compileKotlin`
Expected: BUILD FAILED (MemoryDebugStoreFactory references old Intents — expected, will fix in Task 2)

- [ ] **Step 3: Commit**

```bash
git add modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/debug/memory/MemoryDebugStore.kt
git commit -m "refactor(ui): update MemoryDebugStore with context-aware state and CRUD intents"
```

---

### Task 2: Rewrite MemoryDebugStoreFactory

**Files:**
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/debug/memory/MemoryDebugStoreFactory.kt`

- [ ] **Step 1: Replace the full file content**

```kotlin
package com.ai.challenge.ui.debug.memory

import arrow.core.getOrElse
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.memory.usecase.GetMemoryUseCase
import com.ai.challenge.core.memory.usecase.UpdateFactsUseCase
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

/**
 * Factory that creates [MemoryDebugStore] instances.
 *
 * Wires use cases for memory operations and SessionService for context-aware display.
 * Facts support full CRUD with per-row save. Summaries are read-only.
 */
class MemoryDebugStoreFactory(
    private val storeFactory: StoreFactory,
    private val getMemoryUseCase: GetMemoryUseCase,
    private val updateFactsUseCase: UpdateFactsUseCase,
    private val sessionService: SessionService,
) {

    fun create(): MemoryDebugStore =
        object : MemoryDebugStore,
            Store<MemoryDebugStore.Intent, MemoryDebugStore.State, Nothing> by storeFactory.create(
                name = "MemoryDebugStore",
                initialState = MemoryDebugStore.State(
                    sessionId = null,
                    contextManagementType = null,
                    facts = emptyList(),
                    summaries = emptyList(),
                    selectedCategories = FactCategory.entries.toSet(),
                    isLoading = false,
                    error = null,
                ),
                executorFactory = { ExecutorImpl() },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data object Loading : Msg
        data class Loaded(
            val sessionId: AgentSessionId,
            val contextManagementType: ContextManagementType?,
            val facts: List<Fact>,
            val summaries: List<Summary>,
        ) : Msg
        data class Error(val message: String) : Msg
        data class FactsUpdated(val facts: List<Fact>) : Msg
        data class CategoriesChanged(val selectedCategories: Set<FactCategory>) : Msg
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<MemoryDebugStore.Intent, Nothing, MemoryDebugStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: MemoryDebugStore.Intent) {
            when (intent) {
                is MemoryDebugStore.Intent.LoadMemory -> loadMemory(sessionId = intent.sessionId)
                is MemoryDebugStore.Intent.SaveFact -> saveFact(index = intent.index, fact = intent.fact)
                is MemoryDebugStore.Intent.DeleteFact -> deleteFact(index = intent.index)
                is MemoryDebugStore.Intent.AddFact -> addFact()
                is MemoryDebugStore.Intent.ToggleCategory -> toggleCategory(category = intent.category)
                is MemoryDebugStore.Intent.SelectAllCategories -> dispatch(
                    message = Msg.CategoriesChanged(selectedCategories = FactCategory.entries.toSet()),
                )
            }
        }

        private fun loadMemory(sessionId: AgentSessionId) {
            dispatch(message = Msg.Loading)
            scope.launch {
                val session = sessionService.get(id = sessionId).getOrElse { null }
                val snapshot = getMemoryUseCase.execute(sessionId = sessionId)
                dispatch(
                    message = Msg.Loaded(
                        sessionId = sessionId,
                        contextManagementType = session?.contextManagementType,
                        facts = snapshot.facts,
                        summaries = snapshot.summaries,
                    ),
                )
            }
        }

        private fun saveFact(index: Int, fact: Fact) {
            val sessionId = state().sessionId ?: return
            val currentFacts = state().facts.toMutableList()
            if (index in currentFacts.indices) {
                currentFacts[index] = fact
            } else {
                currentFacts.add(element = fact)
            }
            scope.launch {
                updateFactsUseCase.execute(sessionId = sessionId, facts = currentFacts).fold(
                    ifLeft = { dispatch(message = Msg.Error(message = it.message)) },
                    ifRight = { dispatch(message = Msg.FactsUpdated(facts = currentFacts)) },
                )
            }
        }

        private fun deleteFact(index: Int) {
            val sessionId = state().sessionId ?: return
            val currentFacts = state().facts.toMutableList()
            if (index !in currentFacts.indices) return
            currentFacts.removeAt(index = index)
            scope.launch {
                updateFactsUseCase.execute(sessionId = sessionId, facts = currentFacts).fold(
                    ifLeft = { dispatch(message = Msg.Error(message = it.message)) },
                    ifRight = { dispatch(message = Msg.FactsUpdated(facts = currentFacts)) },
                )
            }
        }

        private fun addFact() {
            val sessionId = state().sessionId ?: return
            val newFact = Fact(
                sessionId = sessionId,
                category = FactCategory.Goal,
                key = FactKey(value = ""),
                value = FactValue(value = ""),
            )
            val updatedFacts = state().facts + newFact
            scope.launch {
                updateFactsUseCase.execute(sessionId = sessionId, facts = updatedFacts).fold(
                    ifLeft = { dispatch(message = Msg.Error(message = it.message)) },
                    ifRight = { dispatch(message = Msg.FactsUpdated(facts = updatedFacts)) },
                )
            }
        }

        private fun toggleCategory(category: FactCategory) {
            val current = state().selectedCategories
            val updated = if (category in current) {
                current - category
            } else {
                current + category
            }
            dispatch(message = Msg.CategoriesChanged(selectedCategories = updated))
        }
    }

    private object ReducerImpl : Reducer<MemoryDebugStore.State, Msg> {
        override fun MemoryDebugStore.State.reduce(msg: Msg): MemoryDebugStore.State =
            when (msg) {
                is Msg.Loading -> copy(isLoading = true, error = null)
                is Msg.Loaded -> copy(
                    sessionId = msg.sessionId,
                    contextManagementType = msg.contextManagementType,
                    facts = msg.facts,
                    summaries = msg.summaries,
                    isLoading = false,
                    error = null,
                )
                is Msg.Error -> copy(isLoading = false, error = msg.message)
                is Msg.FactsUpdated -> copy(facts = msg.facts)
                is Msg.CategoriesChanged -> copy(selectedCategories = msg.selectedCategories)
            }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :modules:presentation:compose-ui:compileKotlin`
Expected: BUILD FAILED (MemoryDebugScreen still uses old types — expected, will fix in Task 3)

- [ ] **Step 3: Commit**

```bash
git add modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/debug/memory/MemoryDebugStoreFactory.kt
git commit -m "refactor(ui): rewrite MemoryDebugStoreFactory with CRUD, filtering, context-aware loading"
```

---

### Task 3: Rewrite MemoryDebugScreen composable

**Files:**
- Modify: `modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/debug/memory/MemoryDebugScreen.kt`

- [ ] **Step 1: Replace the full file content**

```kotlin
package com.ai.challenge.ui.debug.memory

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.summary.Summary

private val PANEL_WIDTH = 400.dp

@Composable
fun MemoryDebugPanel(
    component: MemoryDebugComponent,
    visible: Boolean,
) {
    val animatedWidth by animateDpAsState(
        targetValue = if (visible) PANEL_WIDTH else 0.dp,
        animationSpec = tween(durationMillis = 300),
    )

    if (animatedWidth > 0.dp) {
        Row(
            modifier = Modifier
                .width(width = animatedWidth)
                .fillMaxHeight()
                .clipToBounds(),
        ) {
            Row(
                modifier = Modifier
                    .width(width = PANEL_WIDTH)
                    .fillMaxHeight(),
            ) {
                VerticalDivider()
                MemoryDebugContent(component = component)
            }
        }
    }
}

@Composable
private fun MemoryDebugContent(component: MemoryDebugComponent) {
    val state by component.state.collectAsState()

    Column(
        modifier = Modifier.width(width = PANEL_WIDTH).fillMaxHeight().padding(all = 12.dp),
    ) {
        Text(text = "Memory Debug", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(height = 8.dp))

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(alignment = Alignment.CenterHorizontally))
            return@Column
        }

        state.error?.let { error ->
            Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(height = 4.dp))
        }

        if (state.sessionId == null) {
            Text(text = "No session selected", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        val showFacts = state.contextManagementType is ContextManagementType.StickyFacts
        val showSummaries = state.contextManagementType is ContextManagementType.SummarizeOnThreshold

        if (!showFacts && !showSummaries) {
            Text(
                text = "Current strategy does not use memory.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(weight = 1f)) {
            if (showFacts) {
                item {
                    Text(text = "Facts (${state.facts.size})", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(height = 4.dp))
                    CategoryFilterChips(
                        selectedCategories = state.selectedCategories,
                        onToggle = { component.onIntent(intent = MemoryDebugStore.Intent.ToggleCategory(category = it)) },
                        onSelectAll = { component.onIntent(intent = MemoryDebugStore.Intent.SelectAllCategories) },
                    )
                    Spacer(modifier = Modifier.height(height = 8.dp))
                }

                val filteredFacts = state.facts.withIndex()
                    .filter { it.value.category in state.selectedCategories }
                    .toList()

                itemsIndexed(
                    items = filteredFacts,
                    key = { _, indexed -> "${indexed.index}-${indexed.value.category}-${indexed.value.key.value}" },
                ) { _, indexed ->
                    EditableFactRow(
                        fact = indexed.value,
                        sessionId = state.sessionId!!,
                        onSave = { updatedFact ->
                            component.onIntent(
                                intent = MemoryDebugStore.Intent.SaveFact(index = indexed.index, fact = updatedFact),
                            )
                        },
                        onDelete = {
                            component.onIntent(intent = MemoryDebugStore.Intent.DeleteFact(index = indexed.index))
                        },
                    )
                    Spacer(modifier = Modifier.height(height = 4.dp))
                }

                item {
                    TextButton(
                        onClick = { component.onIntent(intent = MemoryDebugStore.Intent.AddFact) },
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(size = 16.dp))
                        Text(text = "Add Fact", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            if (showSummaries) {
                item {
                    Text(text = "Summaries (${state.summaries.size})", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(height = 8.dp))
                }

                val summaries = state.summaries
                items(
                    count = summaries.size,
                    key = { i -> "${summaries[i].fromTurnIndex.value}-${summaries[i].toTurnIndex.value}-${summaries[i].createdAt.value}" },
                ) { i ->
                    SummaryCard(summary = summaries[i])
                    Spacer(modifier = Modifier.height(height = 8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryFilterChips(
    selectedCategories: Set<FactCategory>,
    onToggle: (FactCategory) -> Unit,
    onSelectAll: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        FilterChip(
            selected = selectedCategories.size == FactCategory.entries.size,
            onClick = onSelectAll,
            label = { Text(text = "All", style = MaterialTheme.typography.labelSmall) },
        )
        for (category in FactCategory.entries) {
            FilterChip(
                selected = category in selectedCategories,
                onClick = { onToggle(category) },
                label = { Text(text = category.name, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableFactRow(
    fact: Fact,
    sessionId: com.ai.challenge.core.session.AgentSessionId,
    onSave: (Fact) -> Unit,
    onDelete: () -> Unit,
) {
    var category by remember(key1 = fact) { mutableStateOf(value = fact.category) }
    var key by remember(key1 = fact) { mutableStateOf(value = fact.key.value) }
    var value by remember(key1 = fact) { mutableStateOf(value = fact.value.value) }
    var expanded by remember { mutableStateOf(value = false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(all = 8.dp)) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = category.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(text = "Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true,
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    for (cat in FactCategory.entries) {
                        DropdownMenuItem(
                            text = { Text(text = cat.name) },
                            onClick = {
                                category = cat
                                expanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(height = 4.dp))

            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(text = "Key") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(height = 4.dp))

            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(text = "Value") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall,
                maxLines = 3,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(
                    onClick = {
                        onSave(
                            Fact(
                                sessionId = sessionId,
                                category = category,
                                key = FactKey(value = key),
                                value = FactValue(value = value),
                            ),
                        )
                    },
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = "Save fact", modifier = Modifier.size(size = 18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete fact", modifier = Modifier.size(size = 18.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: Summary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(all = 12.dp)) {
            Text(
                text = "Turns ${summary.fromTurnIndex.value}..${summary.toTurnIndex.value}",
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(modifier = Modifier.height(height = 4.dp))
            Text(
                text = summary.content.value,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 5,
            )
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :modules:presentation:compose-ui:compileKotlin`
Expected: BUILD FAILED (Main.kt still uses old MemoryDebugStoreFactory constructor — expected, will fix in Task 4)

- [ ] **Step 3: Commit**

```bash
git add modules/presentation/compose-ui/src/main/kotlin/com/ai/challenge/ui/debug/memory/MemoryDebugScreen.kt
git commit -m "feat(ui): rewrite MemoryDebugScreen with editable facts, category filters, context-aware display"
```

---

### Task 4: Update Main.kt DI wiring

**Files:**
- Modify: `modules/presentation/app/src/main/kotlin/com/ai/challenge/app/Main.kt`

- [ ] **Step 1: Update MemoryDebugStoreFactory construction**

Replace the current `memoryDebugStoreFactory` block:

```kotlin
    val memoryDebugStoreFactory = MemoryDebugStoreFactory(
        storeFactory = mainStoreFactory,
        getMemoryUseCase = koin.get<GetMemoryUseCase>(),
        updateFactsUseCase = koin.get<UpdateFactsUseCase>(),
        addSummaryUseCase = koin.get<AddSummaryUseCase>(),
        deleteSummaryUseCase = koin.get<DeleteSummaryUseCase>(),
    )
```

with:

```kotlin
    val memoryDebugStoreFactory = MemoryDebugStoreFactory(
        storeFactory = mainStoreFactory,
        getMemoryUseCase = koin.get<GetMemoryUseCase>(),
        updateFactsUseCase = koin.get<UpdateFactsUseCase>(),
        sessionService = koin.get<SessionService>(),
    )
```

Remove unused imports:
```kotlin
import com.ai.challenge.core.memory.usecase.AddSummaryUseCase
import com.ai.challenge.core.memory.usecase.DeleteSummaryUseCase
```

- [ ] **Step 2: Verify full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add modules/presentation/app/src/main/kotlin/com/ai/challenge/app/Main.kt
git commit -m "refactor(app): update MemoryDebugStoreFactory DI — replace summary use cases with SessionService"
```

---

### Task 5: Manual verification

- [ ] **Step 1: Start the application**

Run: `OPENROUTER_API_KEY=<key> ./gradlew :modules:presentation:app:run`

- [ ] **Step 2: Test context-aware display**

1. Create a session with strategy **None** → open debug panel → verify "Current strategy does not use memory." message
2. Switch to **StickyFacts** → verify Facts section appears with filter chips and Add button
3. Switch to **SummarizeOnThreshold** → verify only Summaries section appears (read-only cards, no delete buttons)

- [ ] **Step 3: Test Facts CRUD**

1. With StickyFacts strategy, send a message so facts are extracted
2. Open debug panel → verify facts appear as editable cards
3. Edit a fact key/value → click Save → verify fact is updated (reload panel)
4. Click Delete on a fact → verify it disappears
5. Click Add Fact → verify empty card appears → fill in and Save

- [ ] **Step 4: Test category filter**

1. With multiple facts of different categories
2. Toggle filter chips → verify only matching facts shown
3. Click "All" → verify all facts shown again
