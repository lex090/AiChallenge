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
import com.ai.challenge.core.session.AgentSessionId
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
    sessionId: AgentSessionId,
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
