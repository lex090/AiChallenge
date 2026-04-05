package com.ai.challenge.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.challenge.core.ContextStrategyType

@Composable
fun StrategySelector(
    currentStrategy: ContextStrategyType,
    onStrategySelected: (ContextStrategyType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Strategy:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ContextStrategyType.entries.forEach { strategy ->
            FilterChip(
                selected = strategy == currentStrategy,
                onClick = { onStrategySelected(strategy) },
                label = {
                    Text(
                        text = when (strategy) {
                            ContextStrategyType.SlidingWindow -> "Sliding Window"
                            ContextStrategyType.StickyFacts -> "Sticky Facts"
                            ContextStrategyType.Branching -> "Branching"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}
