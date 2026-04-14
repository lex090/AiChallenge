package com.ai.challenge.ui.project

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.ai.challenge.ui.project.store.ProjectSettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow

private val PANEL_WIDTH = 320.dp

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun ProjectSettingsPanel(
    store: ProjectSettingsStore,
    visible: Boolean,
    onClose: () -> Unit,
    onDeleted: () -> Unit,
) {
    val state by store.stateFlow.collectAsState()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onClose()
        }
    }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) {
            onDeleted()
        }
    }

    val animatedWidth by animateDpAsState(
        targetValue = if (visible) PANEL_WIDTH else 0.dp,
        animationSpec = tween(durationMillis = 300),
    )

    if (animatedWidth > 0.dp) {
        Row(
            modifier = Modifier
                .width(animatedWidth)
                .fillMaxHeight()
                .clipToBounds(),
        ) {
            Row(
                modifier = Modifier
                    .width(PANEL_WIDTH)
                    .fillMaxHeight(),
            ) {
                VerticalDivider()
                ProjectSettingsPanelContent(
                    store = store,
                    state = state,
                )
            }
        }
    }
}

@Composable
private fun ProjectSettingsPanelContent(
    store: ProjectSettingsStore,
    state: ProjectSettingsStore.State,
) {
    Surface(
        modifier = Modifier.width(PANEL_WIDTH).fillMaxHeight(),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = if (state.isNewProject) "New Project" else "Project Settings",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.projectName,
                onValueChange = { store.accept(ProjectSettingsStore.Intent.UpdateName(name = it)) },
                label = { Text(text = "Project name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.systemInstructions,
                onValueChange = { store.accept(ProjectSettingsStore.Intent.UpdateInstructions(text = it)) },
                label = { Text(text = "System instructions") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                maxLines = Int.MAX_VALUE,
            )

            Spacer(modifier = Modifier.height(12.dp))

            state.errorText?.let { errorText ->
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = { store.accept(ProjectSettingsStore.Intent.Save) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving && state.projectName.isNotBlank(),
            ) {
                Text(text = if (state.isNewProject) "Create" else "Save")
            }

            if (!state.isNewProject) {
                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { store.accept(ProjectSettingsStore.Intent.Delete) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(text = "Delete project")
                }
            }
        }
    }
}
