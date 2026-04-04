package com.ai.challenge.ui.root

import com.ai.challenge.agent.Agent
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.ui.chat.ChatComponent
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.mvikotlin.core.store.StoreFactory
import kotlinx.serialization.Serializable

class RootComponent(
    componentContext: ComponentContext,
    private val storeFactory: StoreFactory,
    private val agent: Agent,
    private val sessionManager: AgentSessionManager,
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    val childStack: Value<ChildStack<*, Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = Config.Chat,
            handleBackButton = false,
            childFactory = ::createChild,
        )

    private fun createChild(config: Config, componentContext: ComponentContext): Child =
        when (config) {
            is Config.Chat -> Child.Chat(
                ChatComponent(
                    componentContext = componentContext,
                    storeFactory = storeFactory,
                    agent = agent,
                    sessionManager = sessionManager,
                )
            )
        }

    sealed interface Child {
        data class Chat(val component: ChatComponent) : Child
    }

    @Serializable
    sealed interface Config {
        @Serializable
        data object Chat : Config
    }
}
