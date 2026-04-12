package com.ai.challenge.ui.debug.memory

import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.memory.usecase.AddSummaryUseCase
import com.ai.challenge.core.memory.usecase.DeleteSummaryUseCase
import com.ai.challenge.core.memory.usecase.GetMemoryUseCase
import com.ai.challenge.core.memory.usecase.UpdateFactsUseCase
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.summary.Summary
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

/**
 * Factory that creates [MemoryDebugStore] instances.
 *
 * Wires use cases for memory CRUD operations into the MVIKotlin store lifecycle.
 * Intended for the debug panel — allows inspecting and editing facts/summaries
 * attached to a session.
 */
class MemoryDebugStoreFactory(
    private val storeFactory: StoreFactory,
    private val getMemoryUseCase: GetMemoryUseCase,
    private val updateFactsUseCase: UpdateFactsUseCase,
    private val addSummaryUseCase: AddSummaryUseCase,
    private val deleteSummaryUseCase: DeleteSummaryUseCase,
) {

    fun create(): MemoryDebugStore =
        object : MemoryDebugStore,
            Store<MemoryDebugStore.Intent, MemoryDebugStore.State, Nothing> by storeFactory.create(
                name = "MemoryDebugStore",
                initialState = MemoryDebugStore.State(
                    sessionId = null,
                    facts = emptyList(),
                    summaries = emptyList(),
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
            val facts: List<Fact>,
            val summaries: List<Summary>,
        ) : Msg
        data class Error(val message: String) : Msg
        data class FactsUpdated(val facts: List<Fact>) : Msg
        data class SummaryAdded(val summary: Summary) : Msg
        data class SummaryDeleted(val summary: Summary) : Msg
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<MemoryDebugStore.Intent, Nothing, MemoryDebugStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: MemoryDebugStore.Intent) {
            when (intent) {
                is MemoryDebugStore.Intent.LoadMemory -> loadMemory(sessionId = intent.sessionId)
                is MemoryDebugStore.Intent.ReplaceFacts -> replaceFacts(facts = intent.facts)
                is MemoryDebugStore.Intent.AddSummary -> addSummary(summary = intent.summary)
                is MemoryDebugStore.Intent.DeleteSummary -> deleteSummary(summary = intent.summary)
            }
        }

        private fun loadMemory(sessionId: AgentSessionId) {
            dispatch(message = Msg.Loading)
            scope.launch {
                val snapshot = getMemoryUseCase.execute(sessionId = sessionId)
                dispatch(
                    message = Msg.Loaded(
                        sessionId = sessionId,
                        facts = snapshot.facts,
                        summaries = snapshot.summaries,
                    )
                )
            }
        }

        private fun replaceFacts(facts: List<Fact>) {
            val sessionId = state().sessionId ?: return
            scope.launch {
                updateFactsUseCase.execute(sessionId = sessionId, facts = facts).fold(
                    ifLeft = { dispatch(message = Msg.Error(message = it.message)) },
                    ifRight = { dispatch(message = Msg.FactsUpdated(facts = facts)) },
                )
            }
        }

        private fun addSummary(summary: Summary) {
            scope.launch {
                addSummaryUseCase.execute(summary = summary).fold(
                    ifLeft = { dispatch(message = Msg.Error(message = it.message)) },
                    ifRight = { dispatch(message = Msg.SummaryAdded(summary = summary)) },
                )
            }
        }

        private fun deleteSummary(summary: Summary) {
            scope.launch {
                deleteSummaryUseCase.execute(summary = summary).fold(
                    ifLeft = { dispatch(message = Msg.Error(message = it.message)) },
                    ifRight = { dispatch(message = Msg.SummaryDeleted(summary = summary)) },
                )
            }
        }
    }

    private object ReducerImpl : Reducer<MemoryDebugStore.State, Msg> {
        override fun MemoryDebugStore.State.reduce(msg: Msg): MemoryDebugStore.State =
            when (msg) {
                is Msg.Loading -> copy(isLoading = true, error = null)
                is Msg.Loaded -> copy(
                    sessionId = msg.sessionId,
                    facts = msg.facts,
                    summaries = msg.summaries,
                    isLoading = false,
                    error = null,
                )
                is Msg.Error -> copy(isLoading = false, error = msg.message)
                is Msg.FactsUpdated -> copy(facts = msg.facts)
                is Msg.SummaryAdded -> copy(summaries = summaries + msg.summary)
                is Msg.SummaryDeleted -> copy(summaries = summaries.filter { it != msg.summary })
            }
    }
}
