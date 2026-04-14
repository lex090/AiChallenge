package com.ai.challenge.ui.project.store

import com.ai.challenge.sharedkernel.identity.ProjectId
import com.arkivanov.mvikotlin.core.store.Store

interface ProjectListStore : Store<ProjectListStore.Intent, ProjectListStore.State, ProjectListStore.Label> {

    sealed interface Intent {
        data object LoadProjects : Intent
        data class SelectProject(val id: ProjectId) : Intent
        data object SelectFreeSessions : Intent
        data object DeselectAll : Intent
        data class DeleteProject(val id: ProjectId) : Intent
    }

    data class State(
        val projects: List<ProjectItem>,
        val activeProjectId: ProjectId?,
        val showFreeSessions: Boolean,
        val errorText: String?,
    )

    data class ProjectItem(
        val id: ProjectId,
        val name: String,
        val initial: Char,
    )

    sealed interface Label {
        data class ProjectSelected(val projectId: ProjectId) : Label
        data object FreeSessionsSelected : Label
    }
}
