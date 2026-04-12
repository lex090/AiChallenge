package com.ai.challenge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ai.challenge.app.di.appModule
import com.ai.challenge.conversation.service.BranchService
import com.ai.challenge.conversation.service.ChatService
import com.ai.challenge.conversation.service.SessionService
import com.ai.challenge.conversation.service.UsageQueryService
import com.ai.challenge.conversation.usecase.ApplicationInitService
import com.ai.challenge.conversation.usecase.CreateSessionUseCase
import com.ai.challenge.contextmanagement.usecase.GetMemoryUseCase
import com.ai.challenge.contextmanagement.usecase.UpdateFactsUseCase
import com.ai.challenge.conversation.usecase.DeleteSessionUseCase
import com.ai.challenge.conversation.usecase.SendMessageUseCase
import com.ai.challenge.ui.debug.memory.MemoryDebugStoreFactory
import com.ai.challenge.ui.root.RootComponent
import com.ai.challenge.ui.root.RootContent
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import org.koin.core.context.startKoin
import javax.swing.SwingUtilities

fun main() {
    val koin = startKoin {
        modules(appModule)
    }.koin

    val lifecycle = LifecycleRegistry()

    val mainStoreFactory: StoreFactory = DefaultStoreFactory()

    val memoryDebugStoreFactory = MemoryDebugStoreFactory(
        storeFactory = mainStoreFactory,
        getMemoryUseCase = koin.get<GetMemoryUseCase>(),
        updateFactsUseCase = koin.get<UpdateFactsUseCase>(),
        sessionService = koin.get<SessionService>(),
    )

    val root = runOnUiThread {
        RootComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            storeFactory = mainStoreFactory,
            memoryDebugStoreFactory = memoryDebugStoreFactory,
            sessionService = koin.get<SessionService>(),
            chatService = koin.get<ChatService>(),
            usageService = koin.get<UsageQueryService>(),
            branchService = koin.get<BranchService>(),
            sendMessageUseCase = koin.get<SendMessageUseCase>(),
            createSessionUseCase = koin.get<CreateSessionUseCase>(),
            deleteSessionUseCase = koin.get<DeleteSessionUseCase>(),
            applicationInitService = koin.get<ApplicationInitService>(),
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

private fun <T> runOnUiThread(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) {
        return block()
    }

    var result: T? = null
    SwingUtilities.invokeAndWait { result = block() }
    @Suppress("UNCHECKED_CAST")
    return result as T
}
