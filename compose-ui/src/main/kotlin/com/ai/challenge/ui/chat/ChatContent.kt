package com.ai.challenge.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.ai.challenge.session.RequestMetrics
import com.ai.challenge.ui.model.UiMessage

@Composable
fun ChatContent(component: ChatComponent) {
    val state by component.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages) { message ->
                    val metrics = message.turnId?.let { state.turnMetrics[it] }
                    MessageBubble(message, metrics)
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
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && inputText.isNotBlank() && !state.isLoading) {
                                component.onSendMessage(inputText.trim())
                                inputText = ""
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = { Text("Type a message...") },
                    enabled = !state.isLoading,
                    singleLine = true,
                )
                TextButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            component.onSendMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !state.isLoading,
                ) {
                    Text("Send")
                }
            }
            if (state.sessionMetrics.tokens.totalTokens > 0) {
                SessionMetricsBar(state.sessionMetrics)
            }
    }
}

@Composable
private fun MessageBubble(message: UiMessage, metrics: RequestMetrics?) {
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor)
                    .padding(12.dp),
                color = textColor,
            )
            if (!message.isUser && metrics != null && metrics.tokens.totalTokens > 0) {
                Text(
                    text = formatTurnMetrics(metrics),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SessionMetricsBar(metrics: RequestMetrics) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = formatSessionMetrics(metrics),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatCost(value: Double): String =
    String.format("%.10f", value).trimEnd('0').trimEnd('.')

private fun formatTurnMetrics(metrics: RequestMetrics): String {
    val parts = mutableListOf<String>()
    parts.add("\u2191${metrics.tokens.promptTokens}")
    parts.add("\u2193${metrics.tokens.completionTokens}")
    parts.add("cached:${metrics.tokens.cachedTokens}")
    if (metrics.tokens.reasoningTokens > 0) parts.add("reasoning:${metrics.tokens.reasoningTokens}")
    if (metrics.cost.totalCost > 0) parts.add("$${formatCost(metrics.cost.totalCost)}")
    return parts.joinToString("  ")
}

private fun formatSessionMetrics(metrics: RequestMetrics): String {
    val parts = mutableListOf<String>()
    parts.add("Session: \u2191${metrics.tokens.promptTokens}  \u2193${metrics.tokens.completionTokens}  cached:${metrics.tokens.cachedTokens}")
    if (metrics.cost.totalCost > 0) parts.add("Total: $${formatCost(metrics.cost.totalCost)}")
    if (metrics.cost.upstreamCost > 0) parts.add("Upstream: $${formatCost(metrics.cost.upstreamCost)}")
    return parts.joinToString("  |  ")
}
