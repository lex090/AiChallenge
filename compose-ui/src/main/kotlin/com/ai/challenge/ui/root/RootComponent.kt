package com.ai.challenge.ui.root

import com.ai.challenge.agent.Agent
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.SessionId
import com.ai.challenge.ui.chat.ChatComponent
import com.ai.challenge.ui.sessionlist.store.SessionListStore
import com.ai.challenge.ui.sessionlist.store.SessionListStoreFactory
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

class RootComponent(
    componentContext: ComponentContext,
    private val storeFactory: StoreFactory,
    private val agent: Agent,
    private val sessionManager: AgentSessionManager,
) : ComponentContext by componentContext {

    private val sessionListStore = instanceKeeper.getStore {
        SessionListStoreFactory(storeFactory, sessionManager).create()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionListState: StateFlow<SessionListStore.State> = sessionListStore.stateFlow

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
        // Load sessions on startup; if empty, create first session
        val sessions = sessionManager.listSessions()
        if (sessions.isEmpty()) {
            val id = sessionManager.createSession()
            sessionListStore.accept(SessionListStore.Intent.LoadSessions)
            selectSession(id)
        } else {
            sessionListStore.accept(SessionListStore.Intent.LoadSessions)
            selectSession(sessions.first().id)
        }
    }

    fun selectSession(sessionId: SessionId) {
        sessionListStore.accept(SessionListStore.Intent.SelectSession(sessionId))
        navigation.replaceCurrent(Config.Chat(sessionId = sessionId.value))
    }

    fun createNewSession() {
        val id = sessionManager.createSession()
        sessionListStore.accept(SessionListStore.Intent.LoadSessions)
        selectSession(id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun deleteSession(sessionId: SessionId) {
        sessionManager.deleteSession(sessionId)
        sessionListStore.accept(SessionListStore.Intent.LoadSessions)

        val remaining = sessionManager.listSessions()
        if (remaining.isEmpty()) {
            createNewSession()
        } else {
            val currentActive = sessionListStore.stateFlow.value.activeSessionId
            if (currentActive == sessionId) {
                selectSession(remaining.first().id)
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
                    sessionManager = sessionManager,
                    sessionId = SessionId(config.sessionId),
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
