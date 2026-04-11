package com.ai.challenge.ui.root

import arrow.core.Either
import com.ai.challenge.core.chat.BranchService
import com.ai.challenge.core.chat.ChatService
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.usage.UsageService
import com.ai.challenge.core.usecase.ApplicationInitService
import com.ai.challenge.core.usecase.CreateSessionUseCase
import com.ai.challenge.core.usecase.DeleteSessionUseCase
import com.ai.challenge.core.usecase.SendMessageUseCase
import com.ai.challenge.ui.chat.ChatComponent
import com.ai.challenge.ui.sessionlist.store.SessionListStore
import com.ai.challenge.ui.sessionlist.store.SessionListStoreFactory
import com.ai.challenge.ui.settings.SessionSettingsComponent
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceCurrent
import com.arkivanov.decompose.value.Value
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

class RootComponent(
    componentContext: ComponentContext,
    private val storeFactory: StoreFactory,
    private val sessionService: SessionService,
    private val chatService: ChatService,
    private val usageService: UsageService,
    private val branchService: BranchService,
    private val sendMessageUseCase: SendMessageUseCase,
    private val createSessionUseCase: CreateSessionUseCase,
    private val deleteSessionUseCase: DeleteSessionUseCase,
    private val applicationInitService: ApplicationInitService,
) : ComponentContext by componentContext {

    private val sessionListStore = instanceKeeper.getStore {
        SessionListStoreFactory(storeFactory = storeFactory, sessionService = sessionService).create()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionListState: StateFlow<SessionListStore.State> = sessionListStore.stateFlow

    private val _settingsComponent = MutableStateFlow<SessionSettingsComponent?>(null)
    val settingsComponent: StateFlow<SessionSettingsComponent?> = _settingsComponent.asStateFlow()

    private val navigation = StackNavigation<Config>()

    val childStack: Value<ChildStack<*, Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = Config.Chat(sessionId = ""),
            handleBackButton = false,
            childFactory = ::createChild,
        )

    init {
        runBlocking {
            val newSession = applicationInitService.ensureAtLeastOneSession()
            sessionListStore.accept(SessionListStore.Intent.LoadSessions)
            when (newSession) {
                is Either.Right -> {
                    val session = newSession.value
                    if (session != null) {
                        selectSession(sessionId = session.id)
                    } else {
                        when (val result = sessionService.list()) {
                            is Either.Right -> selectSession(sessionId = result.value.first().id)
                            is Either.Left -> {}
                        }
                    }
                }
                is Either.Left -> {}
            }
        }
    }

    fun selectSession(sessionId: AgentSessionId) {
        sessionListStore.accept(SessionListStore.Intent.SelectSession(sessionId))
        navigation.replaceCurrent(Config.Chat(sessionId = sessionId.value))
    }

    fun createNewSession() {
        runBlocking {
            when (val result = createSessionUseCase.execute(title = SessionTitle(value = ""))) {
                is Either.Right -> {
                    sessionListStore.accept(SessionListStore.Intent.LoadSessions)
                    selectSession(sessionId = result.value.id)
                }
                is Either.Left -> {}
            }
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
            when (deleteSessionUseCase.execute(sessionId = sessionId)) {
                is Either.Right -> {
                    sessionListStore.accept(SessionListStore.Intent.LoadSessions)

                    when (val remaining = sessionService.list()) {
                        is Either.Right -> {
                            if (remaining.value.isEmpty()) {
                                createNewSession()
                            } else {
                                val currentActive = sessionListStore.stateFlow.value.activeSessionId
                                if (currentActive == sessionId) {
                                    selectSession(sessionId = remaining.value.first().id)
                                }
                            }
                        }
                        is Either.Left -> {}
                    }
                }
                is Either.Left -> {}
            }
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
