package com.ai.challenge.ui.model

import com.ai.challenge.session.TokenUsage

data class UiMessage(
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val tokenUsage: TokenUsage = TokenUsage(),
)
