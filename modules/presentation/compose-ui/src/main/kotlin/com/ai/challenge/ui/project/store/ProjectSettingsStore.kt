package com.ai.challenge.ui.project.store

import com.ai.challenge.sharedkernel.identity.ProjectId
import com.arkivanov.mvikotlin.core.store.Store

interface ProjectSettingsStore : Store<ProjectSettingsStore.Intent, ProjectSettingsStore.State, Nothing> {

    sealed interface Intent {
        data class Load(val projectId: ProjectId) : Intent
        data object LoadNew : Intent
        data class UpdateName(val name: String) : Intent
        data class UpdateInstructions(val text: String) : Intent
        data object Save : Intent
        data object Delete : Intent
    }

    data class State(
        val projectId: ProjectId?,
        val isNewProject: Boolean,
        val projectName: String,
        val systemInstructions: String,
        val isSaving: Boolean,
        val isSaved: Boolean,
        val isDeleted: Boolean,
        val errorText: String?,
    )
}
