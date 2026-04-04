package com.ai.challenge.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ai.challenge.agent.Agent
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.UsageManager
import com.ai.challenge.ui.di.appModule
import com.ai.challenge.ui.root.RootComponent
import com.ai.challenge.ui.root.RootContent
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import org.koin.core.context.startKoin

fun main() {
    val koin = startKoin {
        modules(appModule)
    }.koin

    val lifecycle = LifecycleRegistry()
    val rootComponentContext = DefaultComponentContext(lifecycle = lifecycle)

    val root = RootComponent(
        componentContext = rootComponentContext,
        storeFactory = DefaultStoreFactory(),
        agent = koin.get<Agent>(),
        sessionManager = koin.get<AgentSessionManager>(),
        usageManager = koin.get<UsageManager>(),
    )

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "AI Chat",
        ) {
            MaterialTheme {
                RootContent(root)
            }
        }
    }
}
