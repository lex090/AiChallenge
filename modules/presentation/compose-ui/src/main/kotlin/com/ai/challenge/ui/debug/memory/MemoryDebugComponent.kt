package com.ai.challenge.ui.debug.memory

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow

/**
 * Decompose component for the Memory Debug Panel.
 *
 * Owns the [MemoryDebugStore] and exposes its state as a [StateFlow].
 * Provides convenience methods for dispatching intents from the UI layer.
 */
class MemoryDebugComponent(
    componentContext: ComponentContext,
    storeFactory: MemoryDebugStoreFactory,
) : ComponentContext by componentContext {

    private val store = storeFactory.create()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<MemoryDebugStore.State> = store.stateFlow

    fun onIntent(intent: MemoryDebugStore.Intent) {
        store.accept(intent = intent)
    }

    fun loadForSession(sessionId: AgentSessionId) {
        store.accept(intent = MemoryDebugStore.Intent.LoadMemory(sessionId = sessionId))
    }
}
