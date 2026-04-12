package com.ai.challenge.ui.debug.memory

import arrow.core.getOrElse
import com.ai.challenge.contextmanagement.model.ContextManagementType
import com.ai.challenge.contextmanagement.model.Fact
import com.ai.challenge.contextmanagement.model.FactCategory
import com.ai.challenge.contextmanagement.model.FactKey
import com.ai.challenge.contextmanagement.model.FactValue
import com.ai.challenge.contextmanagement.model.Summary
import com.ai.challenge.contextmanagement.usecase.GetMemoryUseCase
import com.ai.challenge.contextmanagement.usecase.UpdateFactsUseCase
import com.ai.challenge.conversation.service.SessionService
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

/**
 * Factory that creates [MemoryDebugStore] instances.
 *
 * Wires use cases for memory operations and SessionService for context-aware display.
 * Facts support full CRUD with per-row save. Summaries are read-only.
 */
class MemoryDebugStoreFactory(
    private val storeFactory: StoreFactory,
    private val getMemoryUseCase: GetMemoryUseCase,
    private val updateFactsUseCase: UpdateFactsUseCase,
    private val sessionService: SessionService,
) {

    fun create(): MemoryDebugStore =
        object : MemoryDebugStore,
            Store<MemoryDebugStore.Intent, MemoryDebugStore.State, Nothing> by storeFactory.create(
                name = "MemoryDebugStore",
                initialState = MemoryDebugStore.State(
                    sessionId = null,
                    contextManagementType = null,
                    facts = emptyList(),
                    summaries = emptyList(),
                    selectedCategories = FactCategory.entries.toSet(),
                    isLoading = false,
                    error = null,
                ),
                executorFactory = { ExecutorImpl() },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data object Loading : Msg
        data class Loaded(
            val sessionId: AgentSessionId,
            val contextManagementType: ContextManagementType?,
            val facts: List<Fact>,
            val summaries: List<Summary>,
        ) : Msg
        data class Error(val message: String) : Msg
        data class FactsUpdated(val facts: List<Fact>) : Msg
        data class CategoriesChanged(val selectedCategories: Set<FactCategory>) : Msg
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<MemoryDebugStore.Intent, Nothing, MemoryDebugStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: MemoryDebugStore.Intent) {
            when (intent) {
                is MemoryDebugStore.Intent.LoadMemory -> loadMemory(sessionId = intent.sessionId)
                is MemoryDebugStore.Intent.SaveFact -> saveFact(index = intent.index, fact = intent.fact)
                is MemoryDebugStore.Intent.DeleteFact -> deleteFact(index = intent.index)
                is MemoryDebugStore.Intent.AddFact -> addFact()
                is MemoryDebugStore.Intent.ToggleCategory -> toggleCategory(category = intent.category)
                is MemoryDebugStore.Intent.SelectAllCategories -> dispatch(
                    message = Msg.CategoriesChanged(selectedCategories = FactCategory.entries.toSet()),
                )
            }
        }

        private fun loadMemory(sessionId: AgentSessionId) {
            dispatch(message = Msg.Loading)
            scope.launch {
                val session = sessionService.get(id = sessionId).getOrElse { null }
                val snapshot = getMemoryUseCase.execute(sessionId = sessionId)
                dispatch(
                    message = Msg.Loaded(
                        sessionId = sessionId,
                        contextManagementType = session?.let {
                            ContextManagementType.fromModeId(contextModeId = it.contextModeId)
                        },
                        facts = snapshot.facts,
                        summaries = snapshot.summaries,
                    ),
                )
            }
        }

        private fun saveFact(index: Int, fact: Fact) {
            val sessionId = state().sessionId ?: return
            val currentFacts = state().facts.toMutableList()
            if (index in currentFacts.indices) {
                currentFacts[index] = fact
            } else {
                currentFacts.add(element = fact)
            }
            scope.launch {
                updateFactsUseCase.execute(sessionId = sessionId, facts = currentFacts).fold(
                    ifLeft = { dispatch(message = Msg.Error(message = it.message)) },
                    ifRight = { dispatch(message = Msg.FactsUpdated(facts = currentFacts)) },
                )
            }
        }

        private fun deleteFact(index: Int) {
            val sessionId = state().sessionId ?: return
            val currentFacts = state().facts.toMutableList()
            if (index !in currentFacts.indices) return
            currentFacts.removeAt(index = index)
            scope.launch {
                updateFactsUseCase.execute(sessionId = sessionId, facts = currentFacts).fold(
                    ifLeft = { dispatch(message = Msg.Error(message = it.message)) },
                    ifRight = { dispatch(message = Msg.FactsUpdated(facts = currentFacts)) },
                )
            }
        }

        private fun addFact() {
            val sessionId = state().sessionId ?: return
            val newFact = Fact(
                sessionId = sessionId,
                category = FactCategory.Goal,
                key = FactKey(value = ""),
                value = FactValue(value = ""),
            )
            val updatedFacts = state().facts + newFact
            scope.launch {
                updateFactsUseCase.execute(sessionId = sessionId, facts = updatedFacts).fold(
                    ifLeft = { dispatch(message = Msg.Error(message = it.message)) },
                    ifRight = { dispatch(message = Msg.FactsUpdated(facts = updatedFacts)) },
                )
            }
        }

        private fun toggleCategory(category: FactCategory) {
            val current = state().selectedCategories
            val updated = if (category in current) {
                current - category
            } else {
                current + category
            }
            dispatch(message = Msg.CategoriesChanged(selectedCategories = updated))
        }
    }

    private object ReducerImpl : Reducer<MemoryDebugStore.State, Msg> {
        override fun MemoryDebugStore.State.reduce(msg: Msg): MemoryDebugStore.State =
            when (msg) {
                is Msg.Loading -> copy(isLoading = true, error = null)
                is Msg.Loaded -> copy(
                    sessionId = msg.sessionId,
                    contextManagementType = msg.contextManagementType,
                    facts = msg.facts,
                    summaries = msg.summaries,
                    isLoading = false,
                    error = null,
                )
                is Msg.Error -> copy(isLoading = false, error = msg.message)
                is Msg.FactsUpdated -> copy(facts = msg.facts)
                is Msg.CategoriesChanged -> copy(selectedCategories = msg.selectedCategories)
            }
    }
}
