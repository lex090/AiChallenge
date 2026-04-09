package com.ai.challenge.ui.root

import com.ai.challenge.core.agent.Agent
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
    private val agent: Agent,
) : ComponentContext by componentContext {

    private val sessionListStore = instanceKeeper.getStore {
        SessionListStoreFactory(storeFactory, agent).create()
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
            val sessions = agent.listSessions()
            if (sessions.isEmpty()) {
                val id = agent.createSession()
                sessionListStore.accept(SessionListStore.Intent.LoadSessions)
                selectSession(id)
            } else {
                sessionListStore.accept(SessionListStore.Intent.LoadSessions)
                selectSession(sessions.first().id)
            }
        }
    }

    fun selectSession(sessionId: AgentSessionId) {
        sessionListStore.accept(SessionListStore.Intent.SelectSession(sessionId))
        navigation.replaceCurrent(Config.Chat(sessionId = sessionId.value))
    }

    fun createNewSession() {
        runBlocking {
            val id = agent.createSession()
            sessionListStore.accept(SessionListStore.Intent.LoadSessions)
            selectSession(id)
        }
    }

    fun toggleSessionSettings(sessionId: AgentSessionId) {
        if (_settingsComponent.value != null) {
            _settingsComponent.value = null
        } else {
            _settingsComponent.value = SessionSettingsComponent(
                componentContext = this,
                storeFactory = storeFactory,
                agent = agent,
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
            agent.deleteSession(sessionId)
            sessionListStore.accept(SessionListStore.Intent.LoadSessions)

            val remaining = agent.listSessions()
            if (remaining.isEmpty()) {
                createNewSession()
            } else {
                val currentActive = sessionListStore.stateFlow.value.activeSessionId
                if (currentActive == sessionId) {
                    selectSession(remaining.first().id)
                }
            }
        }
    }

    private fun createChild(config: Config, componentContext: ComponentContext): Child =
        when (config) {
            is Config.Chat -> Child.Chat(
                ChatComponent(
                    componentContext = componentContext,
                    storeFactory = storeFactory,
                    agent = agent,
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
