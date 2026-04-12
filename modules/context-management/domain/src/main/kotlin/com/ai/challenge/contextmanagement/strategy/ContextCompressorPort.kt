package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.contextmanagement.model.Summary
import com.ai.challenge.contextmanagement.model.SummaryContent
import com.ai.challenge.sharedkernel.vo.TurnSnapshot

/**
 * Port -- compresses a list of turn snapshots into a summary.
 *
 * Used by [SummarizeOnThresholdStrategy] to create compressed
 * summaries of older conversation turns.
 *
 * Implemented in the data/infrastructure layer (e.g., via LLM).
 */
interface ContextCompressorPort {
    suspend fun compress(turns: List<TurnSnapshot>, previousSummary: Summary?): SummaryContent
}
