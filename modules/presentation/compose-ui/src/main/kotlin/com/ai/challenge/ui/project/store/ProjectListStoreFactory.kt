package com.ai.challenge.ui.project.store

import com.ai.challenge.conversation.service.ProjectService
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class ProjectListStoreFactory(
    private val storeFactory: StoreFactory,
    private val projectService: ProjectService,
) {
    fun create(): ProjectListStore =
        object : ProjectListStore,
            Store<ProjectListStore.Intent, ProjectListStore.State, ProjectListStore.Label> by storeFactory.create(
                name = "ProjectListStore",
                initialState = ProjectListStore.State(
                    projects = emptyList(),
                    activeProjectId = null,
                    showFreeSessions = false,
                    errorText = null,
                ),
                executorFactory = { ExecutorImpl(projectService = projectService) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class ProjectsLoaded(val projects: List<ProjectListStore.ProjectItem>) : Msg
        data class ProjectSelected(val id: ProjectId) : Msg
        data object FreeSessionsSelected : Msg
        data object Deselected : Msg
        data class Error(val text: String) : Msg
    }

    private class ExecutorImpl(
        private val projectService: ProjectService,
    ) : CoroutineExecutor<ProjectListStore.Intent, Nothing, ProjectListStore.State, Msg, ProjectListStore.Label>() {

        override fun executeIntent(intent: ProjectListStore.Intent) {
            when (intent) {
                is ProjectListStore.Intent.LoadProjects -> handleLoadProjects()
                is ProjectListStore.Intent.SelectProject -> handleSelectProject(id = intent.id)
                is ProjectListStore.Intent.SelectFreeSessions -> handleSelectFreeSessions()
                is ProjectListStore.Intent.DeselectAll -> handleDeselectAll()
                is ProjectListStore.Intent.DeleteProject -> handleDeleteProject(id = intent.id)
            }
        }

        private fun handleLoadProjects() {
            scope.launch {
                projectService.list()
                    .fold(
                        ifLeft = { error -> dispatch(Msg.Error(text = error.message)) },
                        ifRight = { projectList ->
                            val items = projectList.map { project ->
                                ProjectListStore.ProjectItem(
                                    id = project.id,
                                    name = project.name.value,
                                    initial = project.name.value.firstOrNull() ?: '?',
                                )
                            }
                            dispatch(Msg.ProjectsLoaded(projects = items))
                        },
                    )
            }
        }

        private fun handleSelectProject(id: ProjectId) {
            dispatch(Msg.ProjectSelected(id = id))
            publish(ProjectListStore.Label.ProjectSelected(projectId = id))
        }

        private fun handleSelectFreeSessions() {
            dispatch(Msg.FreeSessionsSelected)
            publish(ProjectListStore.Label.FreeSessionsSelected)
        }

        private fun handleDeselectAll() {
            dispatch(Msg.Deselected)
        }

        private fun handleDeleteProject(id: ProjectId) {
            scope.launch {
                projectService.delete(id = id)
                    .fold(
                        ifLeft = { error -> dispatch(Msg.Error(text = error.message)) },
                        ifRight = { handleLoadProjects() },
                    )
            }
        }
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<ProjectListStore.State, Msg> {
        override fun ProjectListStore.State.reduce(msg: Msg): ProjectListStore.State =
            when (msg) {
                is Msg.ProjectsLoaded -> copy(
                    projects = msg.projects,
                    errorText = null,
                )
                is Msg.ProjectSelected -> copy(
                    activeProjectId = msg.id,
                    showFreeSessions = false,
                    errorText = null,
                )
                is Msg.FreeSessionsSelected -> copy(
                    activeProjectId = null,
                    showFreeSessions = true,
                    errorText = null,
                )
                is Msg.Deselected -> copy(
                    activeProjectId = null,
                    showFreeSessions = false,
                    errorText = null,
                )
                is Msg.Error -> copy(errorText = msg.text)
            }
    }
}
