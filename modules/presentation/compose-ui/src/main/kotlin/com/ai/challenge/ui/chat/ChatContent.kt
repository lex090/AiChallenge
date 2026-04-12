package com.ai.challenge.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.challenge.conversation.model.Branch
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.identity.TurnId
import com.ai.challenge.conversation.model.UsageRecord
import com.ai.challenge.ui.model.UiMessage

@Composable
fun ChatContent(component: ChatComponent) {
    val state by component.state.collectAsState()
    var inputText by remember { mutableStateOf(value = "") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(index = state.messages.size - 1)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(weight = 1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(weight = 1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                items(items = state.messages) { message ->
                    val usage = message.turnId?.let { state.turnUsage[it] }
                    MessageBubble(
                        message = message,
                        usage = usage,
                        isBranchingEnabled = state.isBranchingEnabled,
                        onCreateBranch = { turnId ->
                            component.onCreateBranch(sourceTurnId = turnId)
                        },
                    )
                }
                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(weight = 1f)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && inputText.isNotBlank() && !state.isLoading) {
                                component.onSendMessage(text = inputText.trim())
                                inputText = ""
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = { Text(text = "Type a message...") },
                    enabled = !state.isLoading,
                    singleLine = true,
                )
                TextButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            component.onSendMessage(text = inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !state.isLoading,
                ) {
                    Text(text = "Send")
                }
            }
            if (state.sessionUsage.totalTokens.value > 0) {
                SessionMetricsBar(usage = state.sessionUsage)
            }
        }

        if (state.isBranchingEnabled && state.branches.isNotEmpty()) {
            VerticalDivider()
            BranchPanel(
                branches = state.branches,
                activeBranchId = state.activeBranchId,
                onSwitchBranch = { component.onSwitchBranch(branchId = it) },
                onDeleteBranch = { component.onDeleteBranch(branchId = it) },
            )
        }
    }
}

@Composable
private fun BranchPanel(
    branches: List<Branch>,
    activeBranchId: BranchId?,
    onSwitchBranch: (BranchId) -> Unit,
    onDeleteBranch: (BranchId) -> Unit,
) {
    Surface(
        modifier = Modifier.width(width = 260.dp).fillMaxHeight(),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(all = 16.dp)) {
            Text(
                text = "Branches",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(height = 12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(height = 12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
                for (branch in branches) {
                    BranchItem(
                        branch = branch,
                        isActive = branch.id == activeBranchId,
                        onSwitchBranch = onSwitchBranch,
                        onDeleteBranch = onDeleteBranch,
                    )
                }
            }
        }
    }
}

@Composable
private fun BranchItem(
    branch: Branch,
    isActive: Boolean,
    onSwitchBranch: (BranchId) -> Unit,
    onDeleteBranch: (BranchId) -> Unit,
) {
    val backgroundColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                          else MaterialTheme.colorScheme.surface
    val label = if (branch.isMain) "main" else "Branch #${branch.id.value.take(n = 6)}"

    Surface(
        onClick = { onSwitchBranch(branch.id) },
        shape = RoundedCornerShape(size = 8.dp),
        color = backgroundColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(weight = 1f),
            )
            if (!branch.isMain) {
                IconButton(
                    onClick = { onDeleteBranch(branch.id) },
                    modifier = Modifier.size(size = 24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete branch",
                        modifier = Modifier.size(size = 14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: UiMessage,
    usage: UsageRecord?,
    isBranchingEnabled: Boolean,
    onCreateBranch: (TurnId) -> Unit,
) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        message.isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        message.isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 600.dp),
        ) {
            Text(
                text = message.text,
                modifier = Modifier
                    .clip(shape = RoundedCornerShape(size = 12.dp))
                    .background(color = backgroundColor)
                    .padding(all = 12.dp),
                color = textColor,
            )
            if (!message.isUser && usage != null && usage.totalTokens.value > 0) {
                Text(
                    text = formatTurnMetrics(usage = usage),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            if (!message.isUser && isBranchingEnabled && message.turnId != null) {
                Surface(
                    onClick = { onCreateBranch(message.turnId) },
                    shape = RoundedCornerShape(size = 6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
                    ) {
                        Text(
                            text = "\u2442",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Branch",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionMetricsBar(usage: UsageRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = formatSessionMetrics(usage = usage),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatCost(value: java.math.BigDecimal): String =
    value.stripTrailingZeros().toPlainString()

private fun formatTurnMetrics(usage: UsageRecord): String {
    val parts = mutableListOf<String>()
    parts.add("\u2191${usage.promptTokens.value}")
    parts.add("\u2193${usage.completionTokens.value}")
    parts.add("cached:${usage.cachedTokens.value}")
    if (usage.reasoningTokens.value > 0) parts.add("reasoning:${usage.reasoningTokens.value}")
    parts.addAll(formatCostParts(usage = usage))
    return parts.joinToString(separator = "  ")
}

private fun formatSessionMetrics(usage: UsageRecord): String {
    val parts = mutableListOf<String>()
    parts.add("Session: \u2191${usage.promptTokens.value}  \u2193${usage.completionTokens.value}  cached:${usage.cachedTokens.value}")
    parts.addAll(formatCostParts(usage = usage))
    return parts.joinToString(separator = "  |  ")
}

private fun formatCostParts(usage: UsageRecord): List<String> = buildList {
    add("cost:$${formatCost(value = usage.totalCost.value)}")
    if (usage.upstreamCost.value > java.math.BigDecimal.ZERO && usage.upstreamCost.value != usage.totalCost.value) add("upstream:$${formatCost(value = usage.upstreamCost.value)}")
    add("prompt:$${formatCost(value = usage.upstreamPromptCost.value)}")
    add("completion:$${formatCost(value = usage.upstreamCompletionsCost.value)}")
}
