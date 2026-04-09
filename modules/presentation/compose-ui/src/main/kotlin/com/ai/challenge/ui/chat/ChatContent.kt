package com.ai.challenge.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.cost.CostDetails
import com.ai.challenge.core.token.TokenDetails
import com.ai.challenge.core.turn.TurnId
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
                    val tokens = message.turnId?.let { state.turnTokens[it] }
                    val costs = message.turnId?.let { state.turnCosts[it] }
                    MessageBubble(
                        message = message,
                        tokens = tokens,
                        costs = costs,
                        isBranchingEnabled = state.isBranchingEnabled,
                        onCreateBranch = { name, turnId ->
                            component.onCreateBranch(name = name, parentTurnId = turnId)
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
            if (state.sessionTokens.totalTokens > 0) {
                SessionMetricsBar(tokens = state.sessionTokens, costs = state.sessionCosts)
            }
        }

        if (state.isBranchingEnabled && state.branches.isNotEmpty()) {
            VerticalDivider()
            BranchPanel(
                branches = state.branches,
                activeBranch = state.activeBranch,
                branchParentMap = state.branchParentMap,
                onSwitchBranch = { component.onSwitchBranch(branchId = it) },
                onDeleteBranch = { component.onDeleteBranch(branchId = it) },
            )
        }
    }
}

@Composable
private fun BranchPanel(
    branches: List<Branch>,
    activeBranch: Branch?,
    branchParentMap: Map<BranchId, BranchId?>,
    onSwitchBranch: (BranchId) -> Unit,
    onDeleteBranch: (BranchId) -> Unit,
) {
    val treeItems = sortBranchesForTree(branches = branches, parentMap = branchParentMap)
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

            Column(verticalArrangement = Arrangement.spacedBy(space = 0.dp)) {
                for ((index, item) in treeItems.withIndex()) {
                    val (branch, depth) = item
                    val isLast = run {
                        val nextSameOrHigher = treeItems.drop(n = index + 1).firstOrNull { it.second <= depth }
                        nextSameOrHigher == null || nextSameOrHigher.second < depth
                    }
                    val continuingDepths = mutableSetOf<Int>()
                    for (d in 1 until depth) {
                        val hasMore = treeItems.drop(n = index + 1).any { it.second == d || (it.second < d) }
                        val nextAtD = treeItems.drop(n = index + 1).firstOrNull { it.second <= d }
                        if (nextAtD != null && nextAtD.second == d) {
                            continuingDepths.add(element = d)
                        }
                    }
                    BranchTreeNode(
                        branch = branch,
                        isActive = branch.id == activeBranch?.id,
                        depth = depth,
                        isLastChild = isLast,
                        continuingDepths = continuingDepths,
                        onSwitchBranch = onSwitchBranch,
                        onDeleteBranch = onDeleteBranch,
                    )
                }
            }
        }
    }
}

private val INDENT_WIDTH = 20.dp

@Composable
private fun BranchTreeNode(
    branch: Branch,
    isActive: Boolean,
    depth: Int,
    isLastChild: Boolean,
    continuingDepths: Set<Int>,
    onSwitchBranch: (BranchId) -> Unit,
    onDeleteBranch: (BranchId) -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val backgroundColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                          else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier.fillMaxWidth().height(intrinsicSize = IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (depth > 0) {
            for (d in 1 until depth) {
                val showLine = d in continuingDepths
                Box(
                    modifier = Modifier
                        .width(width = INDENT_WIDTH)
                        .fillMaxHeight()
                        .drawBehind {
                            if (showLine) {
                                drawLine(
                                    color = lineColor,
                                    start = Offset(x = size.width / 2, y = 0f),
                                    end = Offset(x = size.width / 2, y = size.height),
                                    strokeWidth = 1.5f,
                                )
                            }
                        },
                )
            }
            Box(
                modifier = Modifier
                    .width(width = INDENT_WIDTH)
                    .fillMaxHeight()
                    .drawBehind {
                        val midX = size.width / 2
                        val midY = size.height / 2
                        drawLine(
                            color = lineColor,
                            start = Offset(x = midX, y = 0f),
                            end = Offset(x = midX, y = if (isLastChild) midY else size.height),
                            strokeWidth = 1.5f,
                        )
                        drawLine(
                            color = lineColor,
                            start = Offset(x = midX, y = midY),
                            end = Offset(x = size.width, y = midY),
                            strokeWidth = 1.5f,
                        )
                    },
            )
        }
        Surface(
            onClick = { onSwitchBranch(branch.id) },
            shape = RoundedCornerShape(size = 8.dp),
            color = backgroundColor,
            modifier = Modifier.weight(weight = 1f).padding(vertical = 2.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = branch.name,
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
}

@Composable
private fun MessageBubble(
    message: UiMessage,
    tokens: TokenDetails?,
    costs: CostDetails?,
    isBranchingEnabled: Boolean,
    onCreateBranch: (String, TurnId) -> Unit,
) {
    var showBranchDialog by remember { mutableStateOf(value = false) }
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
            if (!message.isUser && tokens != null && costs != null && tokens.totalTokens > 0) {
                Text(
                    text = formatTurnMetrics(tokens = tokens, costs = costs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            if (!message.isUser && isBranchingEnabled && message.turnId != null) {
                Surface(
                    onClick = { showBranchDialog = true },
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
                            text = "⑂",
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

    if (showBranchDialog && message.turnId != null) {
        CreateBranchDialog(
            onDismiss = { showBranchDialog = false },
            onCreate = { name ->
                onCreateBranch(name, message.turnId)
                showBranchDialog = false
            },
        )
    }
}

@Composable
private fun CreateBranchDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf(value = "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Create Branch") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(text = "Branch name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.ifBlank { "Branch ${System.currentTimeMillis() % 1000}" }) },
            ) {
                Text(text = "Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(text = "Cancel")
            }
        },
    )
}

@Composable
private fun SessionMetricsBar(tokens: TokenDetails, costs: CostDetails) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = formatSessionMetrics(tokens = tokens, costs = costs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatCost(value: Double): String =
    String.format("%.10f", value).trimEnd('0').trimEnd('.')

private fun formatTurnMetrics(tokens: TokenDetails, costs: CostDetails): String {
    val parts = mutableListOf<String>()
    parts.add("\u2191${tokens.promptTokens}")
    parts.add("\u2193${tokens.completionTokens}")
    parts.add("cached:${tokens.cachedTokens}")
    if (tokens.reasoningTokens > 0) parts.add("reasoning:${tokens.reasoningTokens}")
    parts.addAll(formatCostParts(cost = costs))
    return parts.joinToString(separator = "  ")
}

private fun formatSessionMetrics(tokens: TokenDetails, costs: CostDetails): String {
    val parts = mutableListOf<String>()
    parts.add("Session: \u2191${tokens.promptTokens}  \u2193${tokens.completionTokens}  cached:${tokens.cachedTokens}")
    parts.addAll(formatCostParts(cost = costs))
    return parts.joinToString(separator = "  |  ")
}

private fun formatCostParts(cost: CostDetails): List<String> = buildList {
    add("cost:$${formatCost(value = cost.totalCost)}")
    if (cost.upstreamCost > 0 && cost.upstreamCost != cost.totalCost) add("upstream:$${formatCost(value = cost.upstreamCost)}")
    add("prompt:$${formatCost(value = cost.upstreamPromptCost)}")
    add("completion:$${formatCost(value = cost.upstreamCompletionsCost)}")
}

private fun computeBranchDepth(
    branchId: BranchId,
    parentMap: Map<BranchId, BranchId?>,
): Int {
    var depth = 0
    var current = parentMap[branchId]
    while (current != null) {
        depth++
        current = parentMap[current]
    }
    return depth
}

private fun sortBranchesForTree(
    branches: List<Branch>,
    parentMap: Map<BranchId, BranchId?>,
): List<Pair<Branch, Int>> {
    fun children(parentId: BranchId?): List<Branch> =
        branches.filter { parentMap[it.id] == parentId }

    fun flatten(parentId: BranchId?, depth: Int): List<Pair<Branch, Int>> =
        children(parentId = parentId).flatMap { branch ->
            listOf(branch to depth) + flatten(parentId = branch.id, depth = depth + 1)
        }

    return flatten(parentId = null, depth = 0)
}
