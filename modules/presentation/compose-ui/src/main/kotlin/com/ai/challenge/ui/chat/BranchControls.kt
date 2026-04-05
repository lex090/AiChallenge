package com.ai.challenge.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.challenge.core.BranchId
import com.ai.challenge.core.BranchNode
import com.ai.challenge.core.BranchTree
import com.ai.challenge.core.CheckpointNode

@Composable
fun BranchControls(
    branchTree: BranchTree?,
    onCreateCheckpoint: () -> Unit,
    onCreateBranch: (String) -> Unit,
    onSwitchBranch: (BranchId) -> Unit,
    onRefresh: () -> Unit,
) {
    var branchName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Branch Controls",
            style = MaterialTheme.typography.titleSmall,
        )

        HorizontalDivider()

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onCreateCheckpoint) {
                Text("Checkpoint", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = onRefresh) {
                Text("Refresh", style = MaterialTheme.typography.labelSmall)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = branchName,
                onValueChange = { branchName = it },
                placeholder = { Text("Branch name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = {
                    if (branchName.isNotBlank()) {
                        onCreateBranch(branchName.trim())
                        branchName = ""
                    }
                },
                enabled = branchName.isNotBlank(),
            ) {
                Text("Branch", style = MaterialTheme.typography.labelSmall)
            }
        }

        HorizontalDivider()

        if (branchTree == null || branchTree.checkpoints.isEmpty()) {
            Text(
                text = "No checkpoints yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "Branch Tree",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(branchTree.checkpoints) { checkpoint ->
                    CheckpointItem(checkpoint, onSwitchBranch)
                }
            }
        }
    }
}

@Composable
private fun CheckpointItem(
    checkpoint: CheckpointNode,
    onSwitchBranch: (BranchId) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
    ) {
        Text(
            text = "Checkpoint @ turn ${checkpoint.turnIndex}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
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
    val bgColor = if (branchNode.isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (branchNode.isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable { onSwitchBranch(branchNode.branch.id) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = branchNode.branch.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (branchNode.isActive) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
        )
        Text(
            text = "${branchNode.turnCount} turns",
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.7f),
        )
    }
}
