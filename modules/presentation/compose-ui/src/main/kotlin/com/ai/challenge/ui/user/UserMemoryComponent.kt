package com.ai.challenge.ui.user

import com.ai.challenge.sharedkernel.identity.UserId
import com.ai.challenge.ui.user.store.UserMemoryStore
import com.ai.challenge.ui.user.store.UserMemoryStoreFactory
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow

/**
 * Decompose component for the User Memory Panel.
 *
 * Owns the [UserMemoryStore] and exposes its state as a [StateFlow].
 * Provides convenience methods for dispatching intents from the UI layer.
 */
class UserMemoryComponent(
    componentContext: ComponentContext,
    storeFactory: UserMemoryStoreFactory,
) : ComponentContext by componentContext {

    private val store = storeFactory.create()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<UserMemoryStore.State> = store.stateFlow

    fun onIntent(intent: UserMemoryStore.Intent) {
        store.accept(intent = intent)
    }

    fun loadForUser(userId: UserId) {
        store.accept(intent = UserMemoryStore.Intent.LoadMemory(userId = userId))
    }
}
