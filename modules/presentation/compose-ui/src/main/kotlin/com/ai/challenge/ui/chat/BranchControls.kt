package com.ai.challenge.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    activeBranchId: BranchId?,
    visible: Boolean,
    onCreateBranch: (String) -> Unit,
    onSwitchBranch: (BranchId) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, modifier = modifier) {
        Surface(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
            ) {
                Text(
                    text = "Branch Controls",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                CreateBranchSection(onCreateBranch = onCreateBranch)

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                Text(
                    text = "Branch Tree",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                if (branchTree == null || branchTree.checkpoints.isEmpty()) {
                    Text(
                        text = "No branches yet. Create a branch at the current conversation point.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    BranchTreeView(
                        branchTree = branchTree,
                        activeBranchId = activeBranchId,
                        onSwitchBranch = onSwitchBranch,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateBranchSection(onCreateBranch: (String) -> Unit) {
    var branchName by remember { mutableStateOf("") }

    Column {
        Text(
            text = "New Branch",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
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
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Create branch",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun BranchTreeView(
    branchTree: BranchTree,
    activeBranchId: BranchId?,
    onSwitchBranch: (BranchId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = "main (${branchTree.mainTurnCount} turns)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (activeBranchId == null) FontWeight.Bold else FontWeight.Normal,
                color = if (activeBranchId == null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        items(branchTree.checkpoints) { checkpoint ->
            CheckpointItem(
                checkpoint = checkpoint,
                activeBranchId = activeBranchId,
                onSwitchBranch = onSwitchBranch,
            )
        }
    }
}

@Composable
private fun CheckpointItem(
    checkpoint: CheckpointNode,
    activeBranchId: BranchId?,
    onSwitchBranch: (BranchId) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Default.AccountTree,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Checkpoint at turn ${checkpoint.turnIndex}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        checkpoint.branches.forEach { branchNode ->
            BranchItem(
                branchNode = branchNode,
                isActive = branchNode.branch.id == activeBranchId,
                onSwitch = { onSwitchBranch(branchNode.branch.id) },
            )
        }
    }
}

@Composable
private fun BranchItem(
    branchNode: BranchNode,
    isActive: Boolean,
    onSwitch: () -> Unit,
) {
    val bgColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onSwitch)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = branchNode.branch.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
        )
        Text(
            text = "${branchNode.turnCount} turns",
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.7f),
        )
    }
}
