package com.ai.challenge.ui.sessionlist.store

import com.ai.challenge.session.SessionId
import com.arkivanov.mvikotlin.core.store.Store
import kotlin.time.Instant

interface SessionListStore : Store<SessionListStore.Intent, SessionListStore.State, Nothing> {

    sealed interface Intent {
        data object LoadSessions : Intent
        data object CreateSession : Intent
        data class DeleteSession(val id: SessionId) : Intent
        data class SelectSession(val id: SessionId) : Intent
    }

    data class State(
        val sessions: List<SessionItem> = emptyList(),
        val activeSessionId: SessionId? = null,
    )

    data class SessionItem(
        val id: SessionId,
        val title: String,
        val updatedAt: Instant,
    )
}
