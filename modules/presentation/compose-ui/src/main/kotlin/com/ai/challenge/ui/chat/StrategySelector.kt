package com.ai.challenge.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.challenge.core.ContextStrategyType

@Composable
fun StrategySelector(
    currentStrategy: ContextStrategyType,
    onStrategySelected: (ContextStrategyType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ContextStrategyType.entries.forEach { strategy ->
            FilterChip(
                selected = strategy == currentStrategy,
                onClick = { onStrategySelected(strategy) },
                label = { Text(strategyLabel(strategy)) },
            )
        }
    }
}

private fun strategyLabel(strategy: ContextStrategyType): String = when (strategy) {
    ContextStrategyType.SlidingWindow -> "Sliding Window"
    ContextStrategyType.StickyFacts -> "Sticky Facts"
    ContextStrategyType.Branching -> "Branching"
}
