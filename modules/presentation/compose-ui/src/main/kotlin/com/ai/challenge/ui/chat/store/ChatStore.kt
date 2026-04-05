package com.ai.challenge.ui.chat.store

import com.ai.challenge.core.cost.CostDetails
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.token.TokenDetails
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store

interface ChatStore : Store<ChatStore.Intent, ChatStore.State, Nothing> {

    sealed interface Intent {
        data class SendMessage(val text: String) : Intent
        data class LoadSession(val sessionId: AgentSessionId) : Intent
    }

    data class State(
        val sessionId: AgentSessionId? = null,
        val messages: List<UiMessage> = emptyList(),
        val isLoading: Boolean = false,
        val turnTokens: Map<TurnId, TokenDetails> = emptyMap(),
        val turnCosts: Map<TurnId, CostDetails> = emptyMap(),
        val sessionTokens: TokenDetails = TokenDetails(),
        val sessionCosts: CostDetails = CostDetails(),
    )
}
