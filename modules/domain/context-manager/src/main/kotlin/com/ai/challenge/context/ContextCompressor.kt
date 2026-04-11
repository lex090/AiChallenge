package com.ai.challenge.context

import com.ai.challenge.core.context.model.SummaryContent
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.turn.Turn

interface ContextCompressor {
    suspend fun compress(turns: List<Turn>, previousSummary: Summary?): SummaryContent
}
