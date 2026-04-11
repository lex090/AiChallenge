package com.ai.challenge.ui.settings

import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.ui.settings.store.SessionSettingsStore
import com.ai.challenge.ui.settings.store.SessionSettingsStoreFactory
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow

class SessionSettingsComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    sessionService: SessionService,
    sessionId: AgentSessionId,
) : ComponentContext by componentContext {

    private val store = instanceKeeper.getStore {
        SessionSettingsStoreFactory(storeFactory = storeFactory, sessionService = sessionService).create()
    }

    init {
        store.accept(SessionSettingsStore.Intent.LoadSettings(sessionId))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SessionSettingsStore.State> = store.stateFlow

    fun onChangeType(type: ContextManagementType) {
        store.accept(SessionSettingsStore.Intent.ChangeContextManagementType(type))
    }
}
