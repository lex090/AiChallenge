package com.ai.challenge.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ai.challenge.core.Agent
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

    val root = runOnUiThread {
        RootComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            storeFactory = DefaultStoreFactory(),
            agent = koin.get<Agent>(),
        )
    }

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
