package com.ai.challenge.ui.project.store

import com.ai.challenge.conversation.model.ProjectName
import com.ai.challenge.conversation.service.ProjectService
import com.ai.challenge.conversation.usecase.CreateProjectUseCase
import com.ai.challenge.conversation.usecase.DeleteProjectUseCase
import com.ai.challenge.conversation.usecase.UpdateProjectUseCase
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.ai.challenge.sharedkernel.vo.SystemInstructions
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class ProjectSettingsStoreFactory(
    private val storeFactory: StoreFactory,
    private val projectService: ProjectService,
    private val createProjectUseCase: CreateProjectUseCase,
    private val updateProjectUseCase: UpdateProjectUseCase,
    private val deleteProjectUseCase: DeleteProjectUseCase,
) {
    fun create(): ProjectSettingsStore =
        object : ProjectSettingsStore,
            Store<ProjectSettingsStore.Intent, ProjectSettingsStore.State, Nothing> by storeFactory.create(
                name = "ProjectSettingsStore",
                initialState = ProjectSettingsStore.State(
                    projectId = null,
                    isNewProject = true,
                    projectName = "",
                    systemInstructions = "",
                    isSaving = false,
                    isSaved = false,
                    isDeleted = false,
                    errorText = null,
                ),
                executorFactory = {
                    ExecutorImpl(
                        projectService = projectService,
                        createProjectUseCase = createProjectUseCase,
                        updateProjectUseCase = updateProjectUseCase,
                        deleteProjectUseCase = deleteProjectUseCase,
                    )
                },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class Loaded(
            val projectId: ProjectId,
            val name: String,
            val instructions: String,
        ) : Msg

        data object LoadedNew : Msg
        data class NameUpdated(val name: String) : Msg
        data class InstructionsUpdated(val text: String) : Msg
        data object Saving : Msg
        data class Saved(val projectId: ProjectId) : Msg
        data object Deleted : Msg
        data class Error(val text: String) : Msg
    }

    private class ExecutorImpl(
        private val projectService: ProjectService,
        private val createProjectUseCase: CreateProjectUseCase,
        private val updateProjectUseCase: UpdateProjectUseCase,
        private val deleteProjectUseCase: DeleteProjectUseCase,
    ) : CoroutineExecutor<ProjectSettingsStore.Intent, Nothing, ProjectSettingsStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: ProjectSettingsStore.Intent) {
            when (intent) {
                is ProjectSettingsStore.Intent.Load -> handleLoad(projectId = intent.projectId)
                is ProjectSettingsStore.Intent.LoadNew -> dispatch(Msg.LoadedNew)
                is ProjectSettingsStore.Intent.UpdateName -> dispatch(Msg.NameUpdated(name = intent.name))
                is ProjectSettingsStore.Intent.UpdateInstructions -> dispatch(Msg.InstructionsUpdated(text = intent.text))
                is ProjectSettingsStore.Intent.Save -> handleSave()
                is ProjectSettingsStore.Intent.Delete -> handleDelete()
            }
        }

        private fun handleLoad(projectId: ProjectId) {
            scope.launch {
                projectService.get(id = projectId)
                    .fold(
                        ifLeft = { error -> dispatch(Msg.Error(text = error.message)) },
                        ifRight = { project ->
                            dispatch(
                                Msg.Loaded(
                                    projectId = project.id,
                                    name = project.name.value,
                                    instructions = project.systemInstructions.value,
                                )
                            )
                        },
                    )
            }
        }

        private fun handleSave() {
            val currentState = state()
            dispatch(Msg.Saving)
            scope.launch {
                if (currentState.isNewProject) {
                    createProjectUseCase.execute(
                        name = ProjectName(value = currentState.projectName),
                        systemInstructions = SystemInstructions(value = currentState.systemInstructions),
                    ).fold(
                        ifLeft = { error -> dispatch(Msg.Error(text = error.message)) },
                        ifRight = { project -> dispatch(Msg.Saved(projectId = project.id)) },
                    )
                } else {
                    val projectId = currentState.projectId ?: return@launch
                    updateProjectUseCase.execute(
                        id = projectId,
                        name = ProjectName(value = currentState.projectName),
                        systemInstructions = SystemInstructions(value = currentState.systemInstructions),
                    ).fold(
                        ifLeft = { error -> dispatch(Msg.Error(text = error.message)) },
                        ifRight = { project -> dispatch(Msg.Saved(projectId = project.id)) },
                    )
                }
            }
        }

        private fun handleDelete() {
            val projectId = state().projectId ?: return
            scope.launch {
                deleteProjectUseCase.execute(projectId = projectId)
                    .fold(
                        ifLeft = { error -> dispatch(Msg.Error(text = error.message)) },
                        ifRight = { dispatch(Msg.Deleted) },
                    )
            }
        }
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<ProjectSettingsStore.State, Msg> {
        override fun ProjectSettingsStore.State.reduce(msg: Msg): ProjectSettingsStore.State =
            when (msg) {
                is Msg.Loaded -> copy(
                    projectId = msg.projectId,
                    isNewProject = false,
                    projectName = msg.name,
                    systemInstructions = msg.instructions,
                    errorText = null,
                )
                is Msg.LoadedNew -> copy(
                    projectId = null,
                    isNewProject = true,
                    projectName = "",
                    systemInstructions = "",
                    isSaving = false,
                    isSaved = false,
                    isDeleted = false,
                    errorText = null,
                )
                is Msg.NameUpdated -> copy(projectName = msg.name)
                is Msg.InstructionsUpdated -> copy(systemInstructions = msg.text)
                is Msg.Saving -> copy(isSaving = true, errorText = null)
                is Msg.Saved -> copy(
                    projectId = msg.projectId,
                    isNewProject = false,
                    isSaving = false,
                    isSaved = true,
                    errorText = null,
                )
                is Msg.Deleted -> copy(
                    isSaving = false,
                    isDeleted = true,
                    errorText = null,
                )
                is Msg.Error -> copy(
                    isSaving = false,
                    errorText = msg.text,
                )
            }
    }
}
