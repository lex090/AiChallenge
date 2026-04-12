package com.ai.challenge.ui.debug.memory

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.summary.Summary

private val PANEL_WIDTH = 350.dp

@Composable
fun MemoryDebugPanel(
    component: MemoryDebugComponent,
    visible: Boolean,
) {
    val animatedWidth by animateDpAsState(
        targetValue = if (visible) PANEL_WIDTH else 0.dp,
        animationSpec = tween(durationMillis = 300),
    )

    if (animatedWidth > 0.dp) {
        Row(
            modifier = Modifier
                .width(width = animatedWidth)
                .fillMaxHeight()
                .clipToBounds(),
        ) {
            Row(
                modifier = Modifier
                    .width(width = PANEL_WIDTH)
                    .fillMaxHeight(),
            ) {
                VerticalDivider()
                MemoryDebugContent(component = component)
            }
        }
    }
}

@Composable
private fun MemoryDebugContent(component: MemoryDebugComponent) {
    val state by component.state.collectAsState()

    Column(
        modifier = Modifier.width(width = PANEL_WIDTH).fillMaxHeight().padding(all = 16.dp),
    ) {
        Text(text = "Memory Debug", style = MaterialTheme.typography.headlineSmall)

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(alignment = Alignment.CenterHorizontally))
            return@Column
        }

        state.error?.let { error ->
            Text(text = error, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(height = 8.dp))
        }

        if (state.sessionId == null) {
            Text(text = "No session selected")
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(weight = 1f)) {
            item {
                Text(text = "Facts (${state.facts.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(height = 8.dp))
            }

            items(items = state.facts, key = { "${it.category}-${it.key.value}" }) { fact ->
                FactRow(fact = fact)
            }

            item {
                Spacer(modifier = Modifier.height(height = 16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(height = 16.dp))
                Text(text = "Summaries (${state.summaries.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(height = 8.dp))
            }

            items(items = state.summaries, key = { "${it.fromTurnIndex.value}-${it.toTurnIndex.value}-${it.createdAt.value}" }) { summary ->
                SummaryCard(
                    summary = summary,
                    onDelete = { component.onIntent(intent = MemoryDebugStore.Intent.DeleteSummary(summary = summary)) },
                )
                Spacer(modifier = Modifier.height(height = 8.dp))
            }
        }
    }
}

@Composable
private fun FactRow(fact: Fact) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = fact.category.name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(width = 80.dp),
        )
        Text(
            text = fact.key.value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(width = 120.dp),
        )
        Text(
            text = fact.value.value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(weight = 1f),
        )
    }
}

@Composable
private fun SummaryCard(summary: Summary, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(all = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Turns ${summary.fromTurnIndex.value}..${summary.toTurnIndex.value}",
                    style = MaterialTheme.typography.labelMedium,
                )
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete summary")
                }
            }
            Text(
                text = summary.content.value,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 5,
            )
        }
    }
}
