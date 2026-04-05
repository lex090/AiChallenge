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
    modifier: Modifier = Modifier,
) {
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

            if (branchTree == null || branchTree.checkpoints.isEmpty()) {
                Text(
                    text = "No branches yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
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
private fun CreateBranchSection(
    messageCount: Int,
    onCreateBranch: (Int, String) -> Unit,
) {
    var branchName by remember { mutableStateOf("") }
    // Turn index is message pairs count (user+agent = 1 turn), default to latest
    val turnCount = messageCount / 2

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Create branch at turn $turnCount",
            style = MaterialTheme.typography.labelMedium,
        )
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
                    onCreateBranch(turnCount, branchName.trim())
                    branchName = ""
                }
            },
            enabled = branchName.isNotBlank(),
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
