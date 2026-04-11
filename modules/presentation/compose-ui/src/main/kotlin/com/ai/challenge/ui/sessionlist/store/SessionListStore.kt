package com.ai.challenge.ui.sessionlist.store

import com.ai.challenge.core.session.AgentSessionId
import com.arkivanov.mvikotlin.core.store.Store
import kotlin.time.Instant

interface SessionListStore : Store<SessionListStore.Intent, SessionListStore.State, Nothing> {

    sealed interface Intent {
        data object LoadSessions : Intent
        data object CreateSession : Intent
        data class DeleteSession(val id: AgentSessionId) : Intent
        data class SelectSession(val id: AgentSessionId) : Intent
    }

    data class State(
        val sessions: List<SessionItem> = emptyList(),
        val activeSessionId: AgentSessionId? = null,
        val errorText: String? = null,
    )

    data class SessionItem(
        val id: AgentSessionId,
        val title: String,
        val updatedAt: Instant,
    )
}
