package com.ai.challenge.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.challenge.core.context.ContextManagementType

@Composable
fun SessionSettingsPanel(
    component: SessionSettingsComponent,
    visible: Boolean,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            VerticalDivider()
            SessionSettingsPanelContent(component)
        }
    }
}

@Composable
private fun SessionSettingsPanelContent(component: SessionSettingsComponent) {
    val state by component.state.collectAsState()

    Surface(
        modifier = Modifier.width(280.dp).fillMaxHeight(),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Session Settings",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Context Management",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                ContextManagementTypeOption(
                    label = "No management",
                    description = "Full history sent as-is",
                    selected = state.currentType is ContextManagementType.None,
                    onClick = { component.onChangeType(ContextManagementType.None) },
                )

                Spacer(modifier = Modifier.height(4.dp))

                ContextManagementTypeOption(
                    label = "Summarize on threshold",
                    description = "Compress via summary when history grows",
                    selected = state.currentType is ContextManagementType.SummarizeOnThreshold,
                    onClick = { component.onChangeType(ContextManagementType.SummarizeOnThreshold) },
                )
            }
        }
    }
}

@Composable
private fun ContextManagementTypeOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
