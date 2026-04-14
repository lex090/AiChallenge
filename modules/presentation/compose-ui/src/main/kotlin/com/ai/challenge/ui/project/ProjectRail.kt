package com.ai.challenge.ui.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.ai.challenge.ui.project.store.ProjectListStore

@Composable
fun ProjectRail(
    state: ProjectListStore.State,
    onNewProject: () -> Unit,
    onSelectProject: (ProjectId) -> Unit,
    onSelectFreeSessions: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onNewProject) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New project",
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items = state.projects, key = { it.id.value }) { project ->
                val isActive = project.id == state.activeProjectId
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onSelectProject(project.id) },
                    shape = RoundedCornerShape(size = 8.dp),
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isActive) BorderStroke(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    ) else null,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = project.initial.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))

        Spacer(modifier = Modifier.height(4.dp))

        val isFreeSessions = state.showFreeSessions
        Surface(
            modifier = Modifier
                .size(40.dp)
                .clickable { onSelectFreeSessions() },
            shape = RoundedCornerShape(size = 8.dp),
            color = if (isFreeSessions) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            border = if (isFreeSessions) BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            ) else null,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Free sessions",
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}
