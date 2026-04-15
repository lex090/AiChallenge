package com.ai.challenge.ui.root

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.ui.chat.ChatContent
import com.ai.challenge.ui.debug.memory.MemoryDebugPanel
import com.ai.challenge.ui.project.ProjectRail
import com.ai.challenge.ui.project.ProjectSettingsPanel
import com.ai.challenge.ui.project.store.ProjectListStore
import com.ai.challenge.ui.sessionlist.store.SessionListStore
import com.ai.challenge.ui.settings.SessionSettingsPanel
import com.ai.challenge.ui.user.UserMemoryPanel
import com.ai.challenge.ui.user.UserSettingsPanel
import com.arkivanov.decompose.extensions.compose.stack.Children

@Composable
fun RootContent(component: RootComponent) {
    val sessionListState by component.sessionListState.collectAsState()
    val projectListState by component.projectListState.collectAsState()
    val userListState by component.userListState.collectAsState()
    val settingsComponent by component.settingsComponent.collectAsState()
    val lastSettingsComponent = remember { mutableStateOf(settingsComponent) }
    if (settingsComponent != null) {
        lastSettingsComponent.value = settingsComponent
    }

    val memoryDebugComponent by component.memoryDebugComponent.collectAsState()
    val lastMemoryDebugComponent = remember { mutableStateOf(memoryDebugComponent) }
    if (memoryDebugComponent != null) {
        lastMemoryDebugComponent.value = memoryDebugComponent
    }

    val projectSettingsStore by component.projectSettingsStore.collectAsState()
    val lastProjectSettingsStore = remember { mutableStateOf(projectSettingsStore) }
    if (projectSettingsStore != null) {
        lastProjectSettingsStore.value = projectSettingsStore
    }

    val userSettingsStore by component.userSettingsStore.collectAsState()
    val lastUserSettingsStore = remember { mutableStateOf(userSettingsStore) }
    if (userSettingsStore != null) {
        lastUserSettingsStore.value = userSettingsStore
    }

    val userMemoryComponent by component.userMemoryComponent.collectAsState()
    val lastUserMemoryComponent = remember { mutableStateOf(userMemoryComponent) }
    if (userMemoryComponent != null) {
        lastUserMemoryComponent.value = userMemoryComponent
    }

    val currentSettings = settingsComponent
    if (currentSettings != null) {
        val settingsState by currentSettings.state.collectAsState()
        LaunchedEffect(settingsState.currentType) {
            component.refreshActiveChatBranches()
        }
    }

    val activeUser = userListState.activeUserId?.let { activeId ->
        userListState.users.find { it.id == activeId }
    }

    val hasContext = projectListState.activeProjectId != null || projectListState.showFreeSessions

    Row(modifier = Modifier.fillMaxSize()) {
        ProjectRail(
            state = projectListState,
            activeUser = activeUser,
            onUserClick = { component.onUserClick() },
            onNewProject = { component.openNewProjectSettings() },
            onSelectProject = { projectId -> component.selectProject(projectId = projectId) },
            onSelectFreeSessions = { component.selectFreeSessions() },
        )

        VerticalDivider()

        if (hasContext) {
            SessionPanel(
                sessionListState = sessionListState,
                projectListState = projectListState,
                onNewSession = { component.createNewSession() },
                onSelectSession = { sessionId -> component.selectSession(sessionId = sessionId) },
                onDeleteSession = { sessionId -> component.deleteSession(sessionId = sessionId) },
                onOpenProjectSettings = {
                    val activeProjectId = projectListState.activeProjectId
                    if (activeProjectId != null) {
                        component.openProjectSettings(projectId = activeProjectId)
                    }
                },
            )

            VerticalDivider()
        }

        Row(modifier = Modifier.weight(1f)) {
            if (!hasContext) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Select a project or free sessions",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (sessionListState.activeSessionId == null) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Create a session to start chatting",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    TopBar(
                        sessionListState = sessionListState,
                        projectListState = projectListState,
                        onToggleSettings = {
                            sessionListState.activeSessionId?.let { component.toggleSessionSettings(sessionId = it) }
                        },
                        onToggleMemoryDebug = {
                            sessionListState.activeSessionId?.let { component.toggleMemoryDebug(sessionId = it) }
                        },
                    )

                    HorizontalDivider()

                    Row(modifier = Modifier.weight(1f)) {
                        Children(
                            stack = component.childStack,
                            modifier = Modifier.weight(1f),
                        ) { child ->
                            when (val instance = child.instance) {
                                is RootComponent.Child.Chat -> ChatContent(instance.component)
                            }
                        }

                        lastSettingsComponent.value?.let { settings ->
                            SessionSettingsPanel(
                                component = settings,
                                visible = settingsComponent != null,
                            )
                        }

                        lastMemoryDebugComponent.value?.let { debugComponent ->
                            MemoryDebugPanel(
                                component = debugComponent,
                                visible = memoryDebugComponent != null,
                            )
                        }

                        lastUserMemoryComponent.value?.let { userMemory ->
                            UserMemoryPanel(
                                component = userMemory,
                                visible = userMemoryComponent != null,
                            )
                        }
                    }
                }
            }

            lastProjectSettingsStore.value?.let { store ->
                ProjectSettingsPanel(
                    store = store,
                    visible = projectSettingsStore != null,
                    onClose = { component.closeProjectSettings() },
                    onDeleted = { component.onProjectDeleted() },
                )
            }

            lastUserSettingsStore.value?.let { store ->
                UserSettingsPanel(
                    store = store,
                    visible = userSettingsStore != null,
                    onClose = { component.closeUserSettings() },
                    onDeleted = { component.onUserDeleted() },
                )
            }
        }
    }
}

@Composable
private fun SessionPanel(
    sessionListState: SessionListStore.State,
    projectListState: ProjectListStore.State,
    onNewSession: () -> Unit,
    onSelectSession: (AgentSessionId) -> Unit,
    onDeleteSession: (AgentSessionId) -> Unit,
    onOpenProjectSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .fillMaxHeight()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val headerText = if (projectListState.activeProjectId != null) {
                val activeProject =
                    projectListState.projects.find { it.id == projectListState.activeProjectId }
                activeProject?.name ?: "Project"
            } else {
                "Free Sessions"
            }

            Text(
                text = headerText,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (projectListState.activeProjectId != null) {
                IconButton(onClick = onOpenProjectSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Project settings",
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(onClick = onNewSession) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Text(text = "New session", modifier = Modifier.padding(start = 8.dp))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items = sessionListState.sessions, key = { it.id.value }) { session ->
                SessionRow(
                    session = session,
                    isActive = session.id == sessionListState.activeSessionId,
                    onSelect = { onSelectSession(session.id) },
                    onDelete = { onDeleteSession(session.id) },
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    sessionListState: SessionListStore.State,
    projectListState: ProjectListStore.State,
    onToggleSettings: () -> Unit,
    onToggleMemoryDebug: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val breadcrumb = buildString {
            if (projectListState.activeProjectId != null) {
                val activeProject = projectListState.projects.find { it.id == projectListState.activeProjectId }
                append(activeProject?.name ?: "Project")
                append(" / ")
            }
            val activeSession = sessionListState.sessions.find { it.id == sessionListState.activeSessionId }
            append(activeSession?.title?.ifEmpty { "New chat" } ?: "AI Chat")
        }

        Text(
            text = breadcrumb,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        IconButton(
            onClick = onToggleSettings,
            enabled = sessionListState.activeSessionId != null,
        ) {
            Icon(imageVector = Icons.Default.Settings, contentDescription = "Session settings")
        }

        IconButton(
            onClick = onToggleMemoryDebug,
            enabled = sessionListState.activeSessionId != null,
        ) {
            Icon(imageVector = Icons.Default.BugReport, contentDescription = "Memory debug")
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionListStore.SessionItem,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = session.title.ifEmpty { "New chat" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete session",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
