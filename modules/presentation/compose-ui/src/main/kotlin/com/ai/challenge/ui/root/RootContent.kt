package com.ai.challenge.ui.root

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.ui.chat.ChatContent
import com.ai.challenge.ui.sessionlist.store.SessionListStore
import com.ai.challenge.ui.settings.SessionSettingsPanel
import com.arkivanov.decompose.extensions.compose.stack.Children
import kotlinx.coroutines.launch

@Composable
fun RootContent(component: RootComponent) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sessionListState by component.sessionListState.collectAsState()
    val settingsComponent by component.settingsComponent.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
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
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(Icons.Default.Menu, contentDescription = "Open sessions")
                }
                Text(
                    text = "AI Chat",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        sessionListState.activeSessionId?.let { component.toggleSessionSettings(it) }
                    },
                    enabled = sessionListState.activeSessionId != null,
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Session settings")
                }
            }

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

                settingsComponent?.let { settings ->
                    SessionSettingsPanel(
                        component = settings,
                        visible = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    state: SessionListStore.State,
    onNewChat: () -> Unit,
    onSelectSession: (AgentSessionId) -> Unit,
    onDeleteSession: (AgentSessionId) -> Unit,
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Sessions",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onNewChat) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("New chat", modifier = Modifier.padding(start = 8.dp))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.sessions, key = { it.id.value }) { session ->
                    SessionRow(
                        session = session,
                        isActive = session.id == state.activeSessionId,
                        onSelect = { onSelectSession(session.id) },
                        onDelete = { onDeleteSession(session.id) },
                    )
                }
            }
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                Icons.Default.Delete,
                contentDescription = "Delete session",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
