package com.ai.challenge.ui.chat.store

import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store

interface ChatStore : Store<ChatStore.Intent, ChatStore.State, Nothing> {

    sealed interface Intent {
        data class SendMessage(val text: String) : Intent
    }

    data class State(
        val messages: List<UiMessage> = emptyList(),
        val isLoading: Boolean = false,
    )
}
