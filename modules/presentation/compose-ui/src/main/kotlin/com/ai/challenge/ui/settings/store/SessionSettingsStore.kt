package com.ai.challenge.ui.settings.store

import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSessionId
import com.arkivanov.mvikotlin.core.store.Store

interface SessionSettingsStore : Store<SessionSettingsStore.Intent, SessionSettingsStore.State, Nothing> {

    sealed interface Intent {
        data class LoadSettings(val sessionId: AgentSessionId) : Intent
        data class ChangeContextManagementType(val type: ContextManagementType) : Intent
    }

    data class State(
        val sessionId: AgentSessionId?,
        val currentType: ContextManagementType,
        val isLoading: Boolean,
        val errorText: String?,
    )
}
