package com.ai.challenge.ui.root

import arrow.core.getOrElse
import com.ai.challenge.conversation.service.BranchService
import com.ai.challenge.conversation.service.ChatService
import com.ai.challenge.conversation.service.ProjectService
import com.ai.challenge.conversation.service.SessionService
import com.ai.challenge.conversation.service.UsageQueryService
import com.ai.challenge.conversation.model.SessionTitle
import com.ai.challenge.conversation.usecase.ApplicationInitService
import com.ai.challenge.conversation.usecase.CreateProjectUseCase
import com.ai.challenge.conversation.usecase.CreateSessionUseCase
import com.ai.challenge.conversation.usecase.DeleteProjectUseCase
import com.ai.challenge.conversation.usecase.DeleteSessionUseCase
import com.ai.challenge.conversation.usecase.ListProjectsUseCase
import com.ai.challenge.conversation.usecase.SendMessageUseCase
import com.ai.challenge.conversation.usecase.UpdateProjectUseCase
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.ai.challenge.sharedkernel.identity.UserId
import com.ai.challenge.ui.chat.ChatComponent
import com.ai.challenge.ui.debug.memory.MemoryDebugComponent
import com.ai.challenge.ui.debug.memory.MemoryDebugStoreFactory
import com.ai.challenge.ui.project.store.ProjectListStore
import com.ai.challenge.ui.project.store.ProjectListStoreFactory
import com.ai.challenge.ui.project.store.ProjectSettingsStore
import com.ai.challenge.ui.project.store.ProjectSettingsStoreFactory
import com.ai.challenge.ui.sessionlist.store.SessionListStore
import com.ai.challenge.ui.sessionlist.store.SessionListStoreFactory
import com.ai.challenge.ui.settings.SessionSettingsComponent
import com.ai.challenge.ui.user.UserMemoryComponent
import com.ai.challenge.ui.user.store.UserListStore
import com.ai.challenge.ui.user.store.UserListStoreFactory
import com.ai.challenge.ui.user.store.UserSettingsStore
import com.ai.challenge.ui.user.store.UserSettingsStoreFactory
import com.ai.challenge.ui.user.store.UserMemoryStoreFactory
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceCurrent
import com.arkivanov.decompose.value.Value
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

class RootComponent(
    componentContext: ComponentContext,
    private val storeFactory: StoreFactory,
    private val sessionService: SessionService,
    private val chatService: ChatService,
    private val usageService: UsageQueryService,
    private val branchService: BranchService,
    private val projectService: ProjectService,
    private val sendMessageUseCase: SendMessageUseCase,
    private val createSessionUseCase: CreateSessionUseCase,
    private val deleteSessionUseCase: DeleteSessionUseCase,
    private val createProjectUseCase: CreateProjectUseCase,
    private val updateProjectUseCase: UpdateProjectUseCase,
    private val deleteProjectUseCase: DeleteProjectUseCase,
    private val listProjectsUseCase: ListProjectsUseCase,
    private val applicationInitService: ApplicationInitService,
    private val memoryDebugStoreFactory: MemoryDebugStoreFactory,
    private val userListStoreFactory: UserListStoreFactory,
    private val userSettingsStoreFactory: UserSettingsStoreFactory,
    private val userMemoryStoreFactory: UserMemoryStoreFactory,
) : ComponentContext by componentContext {

    private val sessionListStore = instanceKeeper.getStore {
        SessionListStoreFactory(storeFactory = storeFactory, sessionService = sessionService).create()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionListState: StateFlow<SessionListStore.State> = sessionListStore.stateFlow

    private val projectListStore = instanceKeeper.getStore {
        ProjectListStoreFactory(storeFactory = storeFactory, projectService = projectService).create()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val projectListState: StateFlow<ProjectListStore.State> = projectListStore.stateFlow

    private val userListStore = instanceKeeper.getStore {
        userListStoreFactory.create()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val userListState: StateFlow<UserListStore.State> = userListStore.stateFlow

    private val _settingsComponent = MutableStateFlow<SessionSettingsComponent?>(null)
    val settingsComponent: StateFlow<SessionSettingsComponent?> = _settingsComponent.asStateFlow()

    private val _memoryDebugComponent = MutableStateFlow<MemoryDebugComponent?>(null)
    val memoryDebugComponent: StateFlow<MemoryDebugComponent?> = _memoryDebugComponent.asStateFlow()

    private val _projectSettingsStore = MutableStateFlow<ProjectSettingsStore?>(null)
    val projectSettingsStore: StateFlow<ProjectSettingsStore?> = _projectSettingsStore.asStateFlow()

    private val _userSettingsStore = MutableStateFlow<UserSettingsStore?>(null)
    val userSettingsStore: StateFlow<UserSettingsStore?> = _userSettingsStore.asStateFlow()

    private val _userMemoryComponent = MutableStateFlow<UserMemoryComponent?>(null)
    val userMemoryComponent: StateFlow<UserMemoryComponent?> = _userMemoryComponent.asStateFlow()

    private val navigation = StackNavigation<Config>()

    val childStack: Value<ChildStack<*, Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = Config.Chat(sessionId = ""),
            handleBackButton = false,
            childFactory = ::createChild,
        )

    private val componentScope = CoroutineScope(Dispatchers.Main)

    init {
        runBlocking {
            applicationInitService.ensureAtLeastOneSession()
                .fold(
                    ifLeft = { error -> println("Failed to initialize session: ${error.message}") },
                    ifRight = { session ->
                        if (session != null) {
                            selectSession(sessionId = session.id)
                        } else {
                            val firstSession = sessionService.list()
                                .getOrElse { emptyList() }
                                .firstOrNull()
                            if (firstSession != null) {
                                selectSession(sessionId = firstSession.id)
                            }
                        }
                    },
                )
            sessionListStore.accept(SessionListStore.Intent.LoadSessions)
            projectListStore.accept(ProjectListStore.Intent.LoadProjects)
            userListStore.accept(UserListStore.Intent.LoadUsers)
        }

        componentScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            projectListStore.labels.collect { label ->
                when (label) {
                    is ProjectListStore.Label.ProjectSelected ->
                        sessionListStore.accept(SessionListStore.Intent.FilterByProject(projectId = label.projectId))
                    is ProjectListStore.Label.FreeSessionsSelected ->
                        sessionListStore.accept(SessionListStore.Intent.FilterByProject(projectId = null))
                }
            }
        }

        componentScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            sessionListStore.labels.collect { label ->
                when (label) {
                    is SessionListStore.Label.ActiveSessionChanged -> {
                        val sessionId = label.sessionId
                        if (sessionId != null) {
                            selectSession(sessionId = sessionId)
                        } else {
                            navigation.replaceCurrent(Config.Chat(sessionId = ""))
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun selectSession(sessionId: AgentSessionId) {
        sessionListStore.accept(SessionListStore.Intent.SelectSession(id = sessionId))
        navigation.replaceCurrent(Config.Chat(sessionId = sessionId.value))
        refreshMemoryDebug()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun refreshMemoryDebug() {
        val sessionId = sessionListStore.stateFlow.value.activeSessionId ?: return
        _memoryDebugComponent.value?.loadForSession(sessionId = sessionId)
    }

    fun createNewSession() {
        runBlocking {
            createSessionUseCase.execute(
                title = SessionTitle(value = ""),
                projectId = projectListState.value.activeProjectId,
                userId = userListState.value.activeUserId,
            ).fold(
                ifLeft = { error -> println("Failed to create session: ${error.message}") },
                ifRight = { session ->
                    sessionListStore.accept(SessionListStore.Intent.LoadSessions)
                    selectSession(sessionId = session.id)
                },
            )
        }
    }

    fun selectProject(projectId: ProjectId) {
        projectListStore.accept(ProjectListStore.Intent.SelectProject(id = projectId))
    }

    fun selectFreeSessions() {
        projectListStore.accept(ProjectListStore.Intent.SelectFreeSessions)
    }

    fun openProjectSettings(projectId: ProjectId) {
        if (_projectSettingsStore.value != null) {
            closeProjectSettings()
            return
        }
        val store = ProjectSettingsStoreFactory(
            storeFactory = storeFactory,
            projectService = projectService,
            createProjectUseCase = createProjectUseCase,
            updateProjectUseCase = updateProjectUseCase,
            deleteProjectUseCase = deleteProjectUseCase,
        ).create()
        store.accept(ProjectSettingsStore.Intent.Load(projectId = projectId))
        _projectSettingsStore.value = store
    }

    fun openNewProjectSettings() {
        if (_projectSettingsStore.value != null) {
            closeProjectSettings()
            return
        }
        val store = ProjectSettingsStoreFactory(
            storeFactory = storeFactory,
            projectService = projectService,
            createProjectUseCase = createProjectUseCase,
            updateProjectUseCase = updateProjectUseCase,
            deleteProjectUseCase = deleteProjectUseCase,
        ).create()
        store.accept(ProjectSettingsStore.Intent.LoadNew)
        _projectSettingsStore.value = store
    }

    fun closeProjectSettings() {
        _projectSettingsStore.value = null
        projectListStore.accept(ProjectListStore.Intent.LoadProjects)
    }

    fun onProjectDeleted() {
        _projectSettingsStore.value = null
        projectListStore.accept(ProjectListStore.Intent.DeselectAll)
        projectListStore.accept(ProjectListStore.Intent.LoadProjects)
    }

    fun openUserSettings(userId: UserId) {
        if (_userSettingsStore.value != null) {
            closeUserSettings()
            return
        }
        val store = userSettingsStoreFactory.create()
        store.accept(UserSettingsStore.Intent.Load(userId = userId))
        _userSettingsStore.value = store
    }

    fun openNewUserSettings() {
        if (_userSettingsStore.value != null) {
            closeUserSettings()
            return
        }
        val store = userSettingsStoreFactory.create()
        store.accept(UserSettingsStore.Intent.LoadNew)
        _userSettingsStore.value = store
    }

    fun closeUserSettings() {
        _userSettingsStore.value = null
        userListStore.accept(UserListStore.Intent.LoadUsers)
    }

    fun onUserDeleted() {
        _userSettingsStore.value = null
        _userMemoryComponent.value = null
        userListStore.accept(UserListStore.Intent.DeselectAll)
        userListStore.accept(UserListStore.Intent.LoadUsers)
    }

    fun selectUser(userId: UserId) {
        userListStore.accept(UserListStore.Intent.SelectUser(id = userId))
    }

    fun onUserClick() {
        val activeUserId = userListState.value.activeUserId
        if (activeUserId != null) {
            openUserSettings(userId = activeUserId)
        } else {
            openNewUserSettings()
        }
    }

    fun toggleUserMemory() {
        val activeUserId = userListState.value.activeUserId ?: return
        if (_userMemoryComponent.value != null) {
            _userMemoryComponent.value = null
        } else {
            val component = UserMemoryComponent(
                componentContext = this,
                storeFactory = userMemoryStoreFactory,
            )
            component.loadForUser(userId = activeUserId)
            _userMemoryComponent.value = component
        }
    }

    fun toggleMemoryDebug(sessionId: AgentSessionId) {
        if (_memoryDebugComponent.value != null) {
            _memoryDebugComponent.value = null
        } else {
            val component = MemoryDebugComponent(
                componentContext = this,
                storeFactory = memoryDebugStoreFactory,
            )
            component.loadForSession(sessionId = sessionId)
            _memoryDebugComponent.value = component
        }
    }

    fun toggleSessionSettings(sessionId: AgentSessionId) {
        if (_settingsComponent.value != null) {
            _settingsComponent.value = null
        } else {
            _settingsComponent.value = SessionSettingsComponent(
                componentContext = this,
                storeFactory = storeFactory,
                sessionService = sessionService,
                sessionId = sessionId,
            )
        }
    }

    fun refreshActiveChatBranches() {
        val activeChild = childStack.value.active.instance
        if (activeChild is Child.Chat) {
            activeChild.component.refreshBranches()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun deleteSession(sessionId: AgentSessionId) {
        runBlocking {
            deleteSessionUseCase.execute(sessionId = sessionId)
                .fold(
                    ifLeft = { error -> println("Failed to delete session: ${error.message}") },
                    ifRight = {
                        sessionListStore.accept(SessionListStore.Intent.LoadSessions)
                    },
                )
        }
    }

    private fun createChild(config: Config, componentContext: ComponentContext): Child =
        when (config) {
            is Config.Chat -> Child.Chat(
                ChatComponent(
                    componentContext = componentContext,
                    storeFactory = storeFactory,
                    sendMessageUseCase = sendMessageUseCase,
                    chatService = chatService,
                    sessionService = sessionService,
                    usageService = usageService,
                    branchService = branchService,
                    sessionId = AgentSessionId(config.sessionId),
                    onTurnRecorded = { refreshMemoryDebug() },
                )
            )
        }

    sealed interface Child {
        data class Chat(val component: ChatComponent) : Child
    }

    @Serializable
    sealed interface Config {
        @Serializable
        data class Chat(val sessionId: String) : Config
    }
}
