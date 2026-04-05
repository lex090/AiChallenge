package com.ai.challenge.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.challenge.core.Fact

@Composable
fun FactsPanel(
    facts: List<Fact>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(260.dp)
            .fillMaxHeight(),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Sticky Facts",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (facts.isEmpty()) {
                Text(
                    text = "No facts extracted yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn {
                    items(facts) { fact ->
                        FactItem(fact)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FactItem(fact: Fact) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = fact.key,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = fact.value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
