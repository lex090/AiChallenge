package com.ai.challenge.ui.debug.memory

import com.ai.challenge.contextmanagement.model.ContextManagementType
import com.ai.challenge.contextmanagement.model.Fact
import com.ai.challenge.contextmanagement.model.FactCategory
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.contextmanagement.model.Summary
import com.arkivanov.mvikotlin.core.store.Store

/**
 * MVIKotlin Store contract for the Memory Debug Panel.
 *
 * Exposes intents to load memory, manage facts (CRUD), filter by category,
 * and a state with context-aware display logic.
 */
interface MemoryDebugStore : Store<MemoryDebugStore.Intent, MemoryDebugStore.State, Nothing> {

    sealed interface Intent {
        data class LoadMemory(val sessionId: AgentSessionId) : Intent
        data class SaveFact(val index: Int, val fact: Fact) : Intent
        data class DeleteFact(val index: Int) : Intent
        data object AddFact : Intent
        data class ToggleCategory(val category: FactCategory) : Intent
        data object SelectAllCategories : Intent
    }

    data class State(
        val sessionId: AgentSessionId?,
        val contextManagementType: ContextManagementType?,
        val facts: List<Fact>,
        val summaries: List<Summary>,
        val selectedCategories: Set<FactCategory>,
        val isLoading: Boolean,
        val error: String?,
    )
}
