package com.ai.challenge.ui.user

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.ai.challenge.contextmanagement.model.FactCategory
import com.ai.challenge.contextmanagement.model.FactKey
import com.ai.challenge.contextmanagement.model.FactValue
import com.ai.challenge.contextmanagement.model.UserFact
import com.ai.challenge.contextmanagement.model.UserNote
import com.ai.challenge.sharedkernel.identity.UserId
import com.ai.challenge.ui.user.store.UserMemoryStore

private val PANEL_WIDTH = 400.dp

@Composable
fun UserMemoryPanel(
    component: UserMemoryComponent,
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
                UserMemoryContent(component = component)
            }
        }
    }
}

@Composable
private fun UserMemoryContent(component: UserMemoryComponent) {
    val state by component.state.collectAsState()

    Column(
        modifier = Modifier.width(width = PANEL_WIDTH).fillMaxHeight().padding(all = 12.dp),
    ) {
        Text(text = "User Memory", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(height = 8.dp))

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(alignment = Alignment.CenterHorizontally))
            return@Column
        }

        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(height = 4.dp))
        }

        if (state.userId == null) {
            Text(text = "No user selected", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        var selectedTab by remember { mutableIntStateOf(value = 0) }
        val tabs = listOf("Notes", "Facts")

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(text = title) },
                )
            }
        }

        Spacer(modifier = Modifier.height(height = 8.dp))

        Column(modifier = Modifier.fillMaxWidth().weight(weight = 1f)) {
            when (selectedTab) {
                0 -> NotesTab(state = state, component = component)
                1 -> FactsTab(state = state, component = component)
            }
        }
    }
}

@Composable
private fun NotesTab(
    state: UserMemoryStore.State,
    component: UserMemoryComponent,
) {
    var showAddForm by remember { mutableStateOf(value = false) }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            TextButton(
                onClick = { showAddForm = !showAddForm },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(size = 16.dp),
                )
                Text(text = "Add Note", modifier = Modifier.padding(start = 4.dp))
            }
            Spacer(modifier = Modifier.height(height = 4.dp))
        }

        if (showAddForm) {
            item {
                NoteEditForm(
                    initialTitle = "",
                    initialContent = "",
                    onSave = { title, content ->
                        component.onIntent(
                            intent = UserMemoryStore.Intent.SaveNote(title = title, content = content),
                        )
                        showAddForm = false
                    },
                    onCancel = { showAddForm = false },
                )
                Spacer(modifier = Modifier.height(height = 8.dp))
            }
        }

        items(
            count = state.notes.size,
            key = { i -> state.notes[i].id.value },
        ) { i ->
            NoteCard(
                note = state.notes[i],
                onUpdate = { title, content ->
                    component.onIntent(
                        intent = UserMemoryStore.Intent.UpdateNote(
                            note = state.notes[i],
                            title = title,
                            content = content,
                        ),
                    )
                },
                onDelete = {
                    component.onIntent(
                        intent = UserMemoryStore.Intent.DeleteNote(note = state.notes[i]),
                    )
                },
            )
            Spacer(modifier = Modifier.height(height = 8.dp))
        }
    }
}

@Composable
private fun NoteCard(
    note: UserNote,
    onUpdate: (String, String) -> Unit,
    onDelete: () -> Unit,
) {
    var editing by remember(key1 = note) { mutableStateOf(value = false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        if (editing) {
            NoteEditForm(
                initialTitle = note.title.value,
                initialContent = note.content.value,
                onSave = { title, content ->
                    onUpdate(title, content)
                    editing = false
                },
                onCancel = { editing = false },
            )
        } else {
            Column(modifier = Modifier.padding(all = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = note.title.value,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(weight = 1f),
                    )
                    Row {
                        IconButton(onClick = { editing = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit note",
                                modifier = Modifier.size(size = 18.dp),
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete note",
                                modifier = Modifier.size(size = 18.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(height = 4.dp))
                Text(
                    text = note.content.value,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 5,
                )
            }
        }
    }
}

@Composable
private fun NoteEditForm(
    initialTitle: String,
    initialContent: String,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var title by remember { mutableStateOf(value = initialTitle) }
    var content by remember { mutableStateOf(value = initialContent) }

    Column(modifier = Modifier.padding(all = 12.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(text = "Title") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(height = 4.dp))
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text(text = "Content") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall,
            maxLines = 5,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text(text = "Cancel")
            }
            IconButton(
                onClick = { onSave(title, content) },
                enabled = title.isNotBlank() && content.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save note",
                    modifier = Modifier.size(size = 18.dp),
                )
            }
        }
    }
}

@Composable
private fun FactsTab(
    state: UserMemoryStore.State,
    component: UserMemoryComponent,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(
            items = state.facts,
            key = { index, fact -> "$index-${fact.category}-${fact.key.value}" },
        ) { index, fact ->
            UserEditableFactRow(
                fact = fact,
                userId = state.userId!!,
                onSave = { updatedFact ->
                    component.onIntent(
                        intent = UserMemoryStore.Intent.SaveFact(index = index, fact = updatedFact),
                    )
                },
                onDelete = {
                    component.onIntent(intent = UserMemoryStore.Intent.DeleteFact(index = index))
                },
            )
            Spacer(modifier = Modifier.height(height = 4.dp))
        }

        item {
            TextButton(
                onClick = { component.onIntent(intent = UserMemoryStore.Intent.AddFact) },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(size = 16.dp),
                )
                Text(text = "Add Fact", modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserEditableFactRow(
    fact: UserFact,
    userId: UserId,
    onSave: (UserFact) -> Unit,
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
                            UserFact(
                                userId = userId,
                                category = category,
                                key = FactKey(value = key),
                                value = FactValue(value = value),
                            ),
                        )
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save fact",
                        modifier = Modifier.size(size = 18.dp),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete fact",
                        modifier = Modifier.size(size = 18.dp),
                    )
                }
            }
        }
    }
}
