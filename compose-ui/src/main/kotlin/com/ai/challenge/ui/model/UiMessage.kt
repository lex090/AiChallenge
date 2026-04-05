package com.ai.challenge.ui.model

import com.ai.challenge.core.TurnId

data class UiMessage(
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val turnId: TurnId? = null,
)
