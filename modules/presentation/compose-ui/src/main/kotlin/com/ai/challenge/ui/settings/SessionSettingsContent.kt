package com.ai.challenge.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.challenge.core.context.ContextManagementType

@Composable
fun SessionSettingsDialog(
    component: SessionSettingsComponent,
    onDismiss: () -> Unit,
) {
    val state by component.state.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp).width(320.dp),
            ) {
                Text(
                    text = "Session Settings",
                    style = MaterialTheme.typography.titleLarge,
                )

                Spacer(modifier = Modifier.height(16.dp))

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

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
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
