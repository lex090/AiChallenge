package com.ai.challenge.ui.sessionlist.store

import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.arkivanov.mvikotlin.core.store.Store
import kotlin.time.Instant

interface SessionListStore : Store<SessionListStore.Intent, SessionListStore.State, Nothing> {

    sealed interface Intent {
        data object LoadSessions : Intent
        data object CreateSession : Intent
        data class DeleteSession(val id: AgentSessionId) : Intent
        data class SelectSession(val id: AgentSessionId) : Intent
        data class FilterByProject(val projectId: ProjectId?) : Intent
    }

    data class State(
        val sessions: List<SessionItem>,
        val activeSessionId: AgentSessionId?,
        val filterProjectId: ProjectId?,
        val showFreeSessions: Boolean,
        val errorText: String?,
    )

    data class SessionItem(
        val id: AgentSessionId,
        val title: String,
        val updatedAt: Instant,
        val projectId: ProjectId?,
    )
}
