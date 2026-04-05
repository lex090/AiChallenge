package com.ai.challenge.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.challenge.core.BranchId
import com.ai.challenge.core.BranchNode
import com.ai.challenge.core.BranchTree
import com.ai.challenge.core.CheckpointNode

@Composable
fun BranchControls(
    branchTree: BranchTree?,
    messageCount: Int,
    onCreateBranch: (checkpointTurnIndex: Int, name: String) -> Unit,
    onSwitchBranch: (BranchId) -> Unit,
    onSwitchToMain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasActiveBranch = branchTree?.checkpoints
        ?.flatMap { it.branches }
        ?.any { it.isActive } == true

    Surface(
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight(),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Branches",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            CreateBranchSection(messageCount, onCreateBranch)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            MainBranchItem(isActive = !hasActiveBranch, onSwitchToMain = onSwitchToMain)

            if (branchTree != null && branchTree.checkpoints.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn {
                    items(branchTree.checkpoints) { checkpoint ->
                        CheckpointItem(checkpoint, onSwitchBranch)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainBranchItem(
    isActive: Boolean,
    onSwitchToMain: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Main",
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = "Full conversation history",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!isActive) {
            TextButton(onClick = onSwitchToMain) {
                Text("Switch")
            }
        } else {
            Text(
                text = "Active",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun CreateBranchSection(
    messageCount: Int,
    onCreateBranch: (Int, String) -> Unit,
) {
    var branchName by remember { mutableStateOf("") }
    val maxTurn = messageCount / 2
    var selectedTurn by remember(maxTurn) { mutableStateOf(maxTurn) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Create branch at turn $selectedTurn / $maxTurn",
            style = MaterialTheme.typography.labelMedium,
        )
        if (maxTurn > 1) {
            Slider(
                value = selectedTurn.toFloat(),
                onValueChange = { selectedTurn = it.toInt() },
                valueRange = 1f..maxTurn.toFloat(),
                steps = (maxTurn - 2).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = branchName,
            onValueChange = { branchName = it },
            placeholder = { Text("Branch name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                if (branchName.isNotBlank()) {
                    onCreateBranch(selectedTurn, branchName.trim())
                    branchName = ""
                }
            },
            enabled = branchName.isNotBlank() && maxTurn > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create Branch")
        }
    }
}

@Composable
private fun CheckpointItem(
    checkpoint: CheckpointNode,
    onSwitchBranch: (BranchId) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "Checkpoint at turn ${checkpoint.turnIndex}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        checkpoint.branches.forEach { branchNode ->
            BranchItem(branchNode, onSwitchBranch)
        }
    }
}

@Composable
private fun BranchItem(
    branchNode: BranchNode,
    onSwitchBranch: (BranchId) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = branchNode.branch.name,
                style = MaterialTheme.typography.bodySmall,
                color = if (branchNode.isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = "${branchNode.turnCount} turns",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!branchNode.isActive) {
            TextButton(onClick = { onSwitchBranch(branchNode.branch.id) }) {
                Text("Switch")
            }
        } else {
            Text(
                text = "Active",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
    }
}
