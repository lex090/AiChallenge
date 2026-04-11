package com.ai.challenge.ui.root

import arrow.core.Either
import com.ai.challenge.core.agent.BranchManager
import com.ai.challenge.core.agent.ChatAgent
import com.ai.challenge.core.agent.SessionManager
import com.ai.challenge.core.agent.UsageTracker
import com.ai.challenge.core.session.AgentSessionId
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
    private val sessionManager: SessionManager,
    private val chatAgent: ChatAgent,
    private val usageTracker: UsageTracker,
    private val branchManager: BranchManager,
) : ComponentContext by componentContext {

    private val sessionListStore = instanceKeeper.getStore {
        SessionListStoreFactory(storeFactory = storeFactory, sessionManager = sessionManager).create()
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
            when (val result = sessionManager.listSessions()) {
                is Either.Right -> {
                    val sessions = result.value
                    if (sessions.isEmpty()) {
                        when (val createResult = sessionManager.createSession(title = "")) {
                            is Either.Right -> {
                                sessionListStore.accept(SessionListStore.Intent.LoadSessions)
                                selectSession(sessionId = createResult.value)
                            }
                            is Either.Left -> {}
                        }
                    } else {
                        sessionListStore.accept(SessionListStore.Intent.LoadSessions)
                        selectSession(sessionId = sessions.first().id)
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
            when (val result = sessionManager.createSession(title = "")) {
                is Either.Right -> {
                    sessionListStore.accept(SessionListStore.Intent.LoadSessions)
                    selectSession(sessionId = result.value)
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
                sessionManager = sessionManager,
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
            when (sessionManager.deleteSession(id = sessionId)) {
                is Either.Right -> {
                    sessionListStore.accept(SessionListStore.Intent.LoadSessions)

                    when (val remaining = sessionManager.listSessions()) {
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
                    chatAgent = chatAgent,
                    sessionManager = sessionManager,
                    usageTracker = usageTracker,
                    branchManager = branchManager,
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
