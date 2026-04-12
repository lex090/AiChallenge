package com.ai.challenge.ui.debug.memory

import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.arkivanov.mvikotlin.core.store.Store

/**
 * MVIKotlin Store contract for the Memory Debug Panel.
 *
 * Exposes intents to load, modify facts and summaries for a given session,
 * and a state that reflects the current memory snapshot, loading status, and errors.
 */
interface MemoryDebugStore : Store<MemoryDebugStore.Intent, MemoryDebugStore.State, Nothing> {

    sealed interface Intent {
        data class LoadMemory(val sessionId: AgentSessionId) : Intent
        data class ReplaceFacts(val facts: List<Fact>) : Intent
        data class AddSummary(val summary: Summary) : Intent
        data class DeleteSummary(val summary: Summary) : Intent
    }

    data class State(
        val sessionId: AgentSessionId?,
        val facts: List<Fact>,
        val summaries: List<Summary>,
        val isLoading: Boolean,
        val error: String?,
    )
}
