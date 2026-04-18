package com.ai.challenge.ui.user

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
import com.ai.challenge.ui.user.store.UserSettingsStore
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi

private val PANEL_WIDTH = 320.dp

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun UserSettingsPanel(
    store: UserSettingsStore,
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
                UserSettingsPanelContent(
                    store = store,
                    state = state,
                )
            }
        }
    }
}

@Composable
private fun UserSettingsPanelContent(
    store: UserSettingsStore,
    state: UserSettingsStore.State,
) {
    Surface(
        modifier = Modifier.width(PANEL_WIDTH).fillMaxHeight(),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = if (state.isNewUser) "New User" else "User Settings",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.userName,
                onValueChange = { store.accept(UserSettingsStore.Intent.UpdateName(name = it)) },
                label = { Text(text = "User name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.preferences,
                onValueChange = { store.accept(UserSettingsStore.Intent.UpdatePreferences(text = it)) },
                label = { Text(text = "Preferences (system prompt)") },
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
                onClick = { store.accept(UserSettingsStore.Intent.Save) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving && state.userName.isNotBlank(),
            ) {
                Text(text = if (state.isNewUser) "Create" else "Save")
            }

            if (!state.isNewUser) {
                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { store.accept(UserSettingsStore.Intent.Delete) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(text = "Delete user")
                }
            }
        }
    }
}
